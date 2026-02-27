variable "project_id" { type = string }
variable "region" {
  type    = string
  default = "us-east1"
}
variable "image_url" { type = string }
variable "commit_sha" { type = string }