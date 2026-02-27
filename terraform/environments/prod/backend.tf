terraform {
  backend "gcs" {
    bucket = "hello-platform-terraform-prod"
    prefix = "env/prod"
  }
}