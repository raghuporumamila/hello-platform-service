gcloud iam workload-identity-pools create "github-pool" \
--project="hello-platform-dev" \
--location="global" \
--display-name="GitHub Actions Pool"


gcloud iam workload-identity-pools providers create-oidc "github-provider" \
--project="hello-platform-dev" \
--location="global" \
--workload-identity-pool="github-pool" \
--display-name="GitHub Provider" \
--attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
--issuer-uri="https://token.actions.githubusercontent.com" \
--attribute-condition="attribute.repository == 'raghuporumamila/hello-platform-service'"

gcloud projects add-iam-policy-binding hello-platform-dev \
--member="serviceAccount:ci-service-account-dev@hello-platform-dev.iam.gserviceaccount.com" \
--role="roles/artifactregistry.writer"

gcloud iam service-accounts add-iam-policy-binding "ci-service-account-dev@hello-platform-dev.iam.gserviceaccount.com" \
--project="hello-platform-dev" \
--role="roles/iam.workloadIdentityUser" \
--member="principalSet://iam.googleapis.com/projects/234670586155/locations/global/workloadIdentityPools/github-pool/attribute.repository/raghuporumamila/hello-platform-service"


projects/123456789012/locations/global/workloadIdentityPools/github-pool/providers/github-provider