# hello-platform-service

A production-grade Spring Boot 3 REST service with a full CI/CD pipeline deploying to Google Cloud Run using GitHub Actions, Docker, and Terraform. It is designed around keyless GCP authentication, immutable image promotion across environments, and infrastructure-as-code practices.


## Table of contents

1. Project structure
2. Application
3. Docker
4. Terraform infrastructure
5. CI pipeline
6. CD pipeline
7. Environments and branching strategy
8. GCP workload identity federation setup
9. Security and vulnerability scanning
10. Running locally


---


## 1. Project structure

```
hello-platform-service/
├── src/
│   └── main/
│       ├── java/com/hello/platform/
│       │   ├── PlatformApplication.java        # Spring Boot entry point
│       │   └── controller/
│       │       └── HelloController.java         # REST endpoints
│       └── resources/
│           ├── application.yml                  # App config
│           └── logback-spring.xml               # JSON structured logging
├── terraform/
│   ├── main.tf                                  # Cloud Run module call
│   ├── variables.tf                             # Input variables
│   └── backend.tf                               # GCS remote state backend
├── .github/
│   └── workflows/
│       ├── ci.yml                               # CI pipeline
│       ├── cd.yml                               # CD pipeline
│       └── cd-actions/tf-setup/action.yml       # Reusable Terraform setup action
├── Dockerfile                                   # Multi-stage build
├── .trivyignore                                 # Suppressed CVEs with justification
└── pom.xml                                      # Maven build config
```


---


## 2. Application

The service is a Spring Boot 3.4 application running on Java 17. It exposes three REST endpoints.

GET / returns the string "Hello Platform".

GET /version returns a JSON object with the commit SHA that was baked into the image at build time. The SHA comes from the APP_COMMIT_SHA environment variable, which is set via a Docker build argument. If running locally without the variable set it defaults to "development".

GET /health returns a JSON object with status "OK". This is a simple liveness check separate from the Spring Actuator endpoints.

The application uses logstash-logback-encoder to emit structured JSON logs to stdout, which Cloud Run captures and forwards to Cloud Logging.

Graceful shutdown is enabled. When the container receives a SIGTERM, Spring waits up to 20 seconds for in-flight requests to finish before exiting. This is configured in application.yml.

Dependencies of note:

- spring-boot-starter-web for the REST layer
- spring-boot-starter-actuator for health and metrics endpoints
- logstash-logback-encoder 7.4 for JSON logging

The pom.xml pins specific versions of Tomcat (10.1.45), the Spring Framework (6.2.11), and Jackson (2.18.6) to override the defaults from the Spring Boot parent BOM. This is done to stay ahead of known CVEs without waiting for a Spring Boot patch release.


---


## 3. Docker

The Dockerfile uses a two-stage build.

Stage 1 uses eclipse-temurin:17-jdk-jammy as the build image. It copies the Maven wrapper and pom first, runs dependency:go-offline to cache dependencies, then copies the source and runs mvn clean package -DskipTests.

Stage 2 uses gcr.io/distroless/java17-debian12:nonroot as the runtime image. Distroless images contain only the JRE and the application — no shell, no package manager, no extra utilities. This significantly reduces the attack surface and the number of CVEs reported by scanners.

The nonroot variant runs as a non-root user by default, satisfying container security best practices without any extra USER instruction beyond the explicit USER nonroot statement included for clarity.

The JAR is copied from the build stage and named app.jar. The app version is passed in as a build argument (APP_VERSION) and used to locate the correct JAR file name from the Maven output.

The container listens on port 8080 by default. The PORT environment variable can override this, which is how Cloud Run injects its dynamic port. The JVM is started with -XX:MaxRAMPercentage=75.0 so it respects the container memory limit rather than seeing total host memory.

COMMIT_SHA and APP_VERSION are accepted as build arguments so the final image carries metadata about the exact code it was built from.

The .dockerignore file excludes the target directory (except the built JAR), log files, the .git directory, and CI-related files to keep the build context small and avoid cache busting on irrelevant changes.


---


## 4. Terraform infrastructure

Terraform manages all GCP infrastructure. The state is stored in a GCS bucket and the backend is configured dynamically at init time using -backend-config flags, so the same code works across environments without committing bucket names to the repo.

The backend.tf file declares the GCS backend with no hardcoded values.

The main.tf file calls a shared Cloud Run module from a separate repository (raghuporumamila/hello-platform-terraform). That module is checked out into the terraform/hello-platform-terraform directory during the pipeline run. This keeps the shared infrastructure logic versioned separately from the application code.

Variables passed to the module are env, project_id, region, container_image, service_name, commit_sha, is_public, and deletion_protection. For all deployments is_public is true and deletion_protection is false.

The service_url is output after apply so it can be observed in the pipeline logs.

Variables are declared in variables.tf. The region defaults to us-east1. All other variables are required and provided at apply time by the pipeline.


---


## 5. CI pipeline

The CI pipeline is defined in .github/workflows/ci.yml and is named "Hello Platform Service CI". It triggers on pull requests to the develop, main, and release/** branches when they are opened, reopened, or synchronized.

The pipeline has five jobs: setup, validate, build-and-scan, get-qa-metadata, and promote-metadata.


### setup job

This job determines the target environment, extracts the Maven project version, and uploads both pieces of information as a pipeline artifact (pipeline-metadata) for the CD pipeline to consume.

Environment mapping:
- PRs targeting main -> prod
- PRs targeting release/* -> qa
- All other PRs -> dev

The job also enforces a branch protection rule: merges to main are only allowed from release/* branches. If a PR to main comes from any other branch the job fails immediately with an error message.

The Maven version is extracted with mvn help:evaluate -Dexpression=project.version and written to app_version.txt. The environment name is written to env_name.txt. Both files are uploaded as a GitHub Actions artifact named pipeline-metadata.


### validate job

Runs Terraform format checking and validation against the terraform directory. It uses the reusable tf-setup action to authenticate with GCP and configure Terraform before running.

terraform fmt -check -recursive fails the build if any .tf files are not formatted correctly.

terraform init -backend=false followed by terraform validate checks the configuration for syntax and internal consistency without connecting to the real backend.

This job always runs against the qa environment to have access to the WIF provider and service account variables.


### build-and-scan job

This is the main build job. It runs after setup and validate pass.

Steps:

1. Checkout the code.
2. Set up JDK 17 with Temurin and Maven cache.
3. Run mvn checkstyle:check to enforce code style rules.
4. Run mvn clean test to execute unit tests.
5. Authenticate with GCP using Workload Identity Federation.
6. Configure Docker to push to Artifact Registry.
7. Build the Docker image locally using docker/build-push-action with load: true so the image is available on the runner without being pushed to a registry. The image is tagged with the commit SHA.
8. Run Trivy vulnerability scanner against the locally loaded image. The scan checks for CRITICAL and HIGH severity vulnerabilities and fails the build (exit-code: 1) if any are found that are not suppressed in .trivyignore.
9. If Trivy passes and the PR is targeting develop, push the image to Artifact Registry tagged with both the commit SHA and the Maven version.

The push step is conditional. It only pushes on PRs targeting develop. PRs targeting release/* or main do not push new images — they either use the validate-only flow or the promote flow described below.


### get-qa-metadata and promote-metadata jobs

These jobs only run when the PR is targeting main (i.e., a release branch is being merged to main for a production deployment).

get-qa-metadata retrieves the QA project ID from the qa environment's GitHub variables.

promote-metadata authenticates with the prod GCP project, then pulls the already-tested image from the QA Artifact Registry, retags it for the prod registry, and pushes it. This means the exact same image that passed QA is what gets deployed to production — no rebuild happens.


---


## 6. CD pipeline

The CD pipeline is defined in .github/workflows/cd.yml and is named "Platform Delivery Pipeline". It triggers on workflow_run events — specifically when the "Hello Platform Service CI" workflow completes successfully.

The pipeline has two jobs: setup and deploy.


### setup job

Downloads the pipeline-metadata artifact that was uploaded by the CI pipeline's setup job. It reads app_version.txt and env_name.txt and exposes them as job outputs for the deploy job.


### deploy job

Checks out the code at the exact commit SHA that triggered the CI run (github.event.workflow_run.head_sha). This is important because the workflow_run event runs in the context of the default branch, so the checkout ref must be set explicitly.

It then runs the tf-setup reusable action to authenticate with GCP and set up Terraform, and runs terraform init and terraform apply with all required variables:

- env: the target environment name
- project_id: from the GitHub environment variable GCP_PROJECT_ID
- service_name: from the GitHub environment variable SERVICE_NAME
- image_url: the full Artifact Registry path including the app version tag
- commit_sha: the triggering commit SHA

The Terraform state backend is initialized with the correct GCS bucket and prefix for the target environment.

A concurrency group (terraform-<branch>) with cancel-in-progress: false ensures that if multiple deployments are queued for the same branch they run sequentially, not in parallel, preventing state corruption.


---


## 7. Environments and branching strategy

There are three environments: dev, qa, and prod. Each environment has its own GCP project, Artifact Registry, and Cloud Run service managed by the same Terraform module with different variable values.

The branching model works as follows:

Feature branches are opened as PRs against develop. CI runs, the image is built and scanned, and if it passes it is pushed to the dev Artifact Registry. After CI succeeds the CD pipeline deploys to dev.

When a release is ready a release/* branch is created and a PR is opened against it (or commits are pushed to it). CI runs validate and build but only pushes to QA Artifact Registry. The CD pipeline deploys to qa.

When the release is fully validated a PR is opened from the release/* branch to main. CI runs, detects it is targeting main, and promotes the QA image to the prod Artifact Registry instead of building a new one. After CI succeeds the CD pipeline deploys to prod.

The rule that only release/* branches can merge to main is enforced at the top of the CI setup job. Any PR from a non-release branch to main fails immediately.


---


## 8. GCP workload identity federation setup

No long-lived service account keys are used anywhere in the pipeline. Authentication is done via Workload Identity Federation, which allows GitHub Actions to exchange a short-lived OIDC token for GCP credentials.

The setup requires the following steps, shown here for the dev environment as an example. Repeat for qa and prod with the appropriate project IDs and service account names.

Create a workload identity pool:

```
gcloud iam workload-identity-pools create "github-pool" \
  --project="hello-platform-dev" \
  --location="global" \
  --display-name="GitHub Actions Pool"
```

Create an OIDC provider in that pool that trusts GitHub's token issuer and maps the repository claim:

```
gcloud iam workload-identity-pools providers create-oidc "github-provider" \
  --project="hello-platform-dev" \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --display-name="GitHub Provider" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-condition="attribute.repository == 'raghuporumamila/hello-platform-service'"
```

The attribute-condition locks the provider to only accept tokens from this specific repository.

Grant the CI service account permission to write to Artifact Registry:

```
gcloud projects add-iam-policy-binding hello-platform-dev \
  --member="serviceAccount:ci-service-account-dev@hello-platform-dev.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"
```

Allow the workload identity pool to impersonate the service account:

```
gcloud iam service-accounts add-iam-policy-binding \
  "ci-service-account-dev@hello-platform-dev.iam.gserviceaccount.com" \
  --project="hello-platform-dev" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/github-pool/attribute.repository/raghuporumamila/hello-platform-service"
```

The WIF_PROVIDER and SA_EMAIL (or CD_SA_EMAIL for the CD pipeline) values are stored as GitHub environment variables. Two separate service accounts are used — one for CI (image push access) and one for CD (Terraform apply access with broader permissions). This follows the principle of least privilege.


---


## 9. Security and vulnerability scanning

Trivy runs on every PR as part of the build-and-scan job. It scans the locally built Docker image for CRITICAL and HIGH severity CVEs before any image is pushed to a registry. If vulnerabilities are found the build fails and the image is not pushed.

The .trivyignore file suppresses three CVEs that have been reviewed and determined to be false positives or not applicable:

- CVE-2023-45853: the vulnerable minizip code is not present in the zlib1g binary included in the image
- CVE-2026-0861: JVM-managed memory prevents control of the glibc memalign parameters that the CVE requires
- CVE-2023-25193: the application does not perform font rendering, so the harfbuzz vulnerability is not reachable

Each suppression has an inline comment in .trivyignore explaining the justification. New CVE suppressions should always include a comment with a clear reason.

Using a distroless base image reduces the total number of packages in the image, which reduces the CVE surface area compared to a full Debian or Alpine base.

The CODEOWNERS file assigns @raghuporumamila as the required reviewer for all files in the repository, ensuring no PR can be merged without review.


---


## 10. Running locally

Prerequisites: Java 17, Maven (or use the included ./mvnw wrapper), Docker.

Build and run the JAR directly:

```
./mvnw clean package
java -jar target/hello-platform-service-1.0.0-SNAPSHOT.jar
```

Build and run with Docker:

```
docker build \
  --build-arg APP_VERSION=1.0.0-SNAPSHOT \
  --build-arg COMMIT_SHA=local \
  -t hello-platform-service:local .

docker run -p 8080:8080 hello-platform-service:local
```

Test the endpoints:

```
curl http://localhost:8080/
curl http://localhost:8080/version
curl http://localhost:8080/health
```

Run tests only:

```
./mvnw test
```

Run checkstyle only:

```
./mvnw checkstyle:check
```
