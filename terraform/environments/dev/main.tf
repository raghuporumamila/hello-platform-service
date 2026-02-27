provider "google" {
  project = var.project_id
  region  = var.region
}

# 1. Create a specific identity for the Prod service (Least Privilege)
resource "google_service_account" "run_sa" {
  account_id   = "platform-runner-dev"
  display_name = "Cloud Run Executor for dev"
}
# 2. Call the module
module "platform_app" {
  source                = "../../hello-platform-terraform/modules/cloud_run"
  env                   = "dev"
  project_id            = var.project_id
  region                = var.region
  container_image       = var.image_url
  commit_sha            = var.commit_sha
  service_account_email = google_service_account.run_sa.email
  is_public             = true
}

output "service_url" {
  value = module.platform_app.service_url
}