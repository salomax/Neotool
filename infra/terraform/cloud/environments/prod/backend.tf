# Remote Backend Configuration
# Configure remote state storage (S3-compatible, e.g., Cloudflare R2 or AWS S3)
# with state locking mechanism

# Uncomment and configure based on your backend choice:

# Option 1: Cloudflare R2 (S3-compatible)
# terraform {
#   backend "s3" {
#     bucket                      = "neotool-terraform-state"
#     key                         = "prod/terraform.tfstate"
#     region                      = "auto"
#     endpoint                    = "https://<account-id>.r2.cloudflarestorage.com"
#     access_key                  = "<r2-access-key-id>"
#     secret_key                  = "<r2-secret-access-key>"
#     skip_credentials_validation = true
#     skip_region_validation      = true
#     skip_metadata_api_check     = true
#     # Note: R2 doesn't support DynamoDB for locking, use alternative locking mechanism
#   }
# }

# Option 2: AWS S3 with DynamoDB locking
# terraform {
#   backend "s3" {
#     bucket         = "neotool-terraform-state"
#     key            = "prod/terraform.tfstate"
#     region         = "us-east-1"
#     dynamodb_table = "terraform-state-lock"
#     encrypt        = true
#   }
# }

# Option 3: Local backend (for development/testing)
# terraform {
#   backend "local" {
#     path = "terraform.tfstate"
#   }
# }

# Note: For production, use a remote backend with state locking.
# Ensure sensitive values (tokens, keys) are not stored in state outputs.

