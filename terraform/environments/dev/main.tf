provider "google" {
  project = var.project_id
  region  = var.region
}

# 2. Call the module
module "platform_app" {
  source                = "../../hello-platform-terraform/modules/cloud_run"
  env                   = "${var.env}"
  project_id            = var.project_id
  region                = var.region
  container_image       = var.image_url
  commit_sha            = var.commit_sha
  is_public             = true
}

output "service_url" {
  value = module.platform_app.service_url
}