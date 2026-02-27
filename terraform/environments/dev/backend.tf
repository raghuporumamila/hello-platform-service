terraform {
  backend "gcs" {
    bucket = "hello-platform-terraform-dev"
    prefix = "env/dev"
  }
}