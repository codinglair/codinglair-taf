variable "VERSION" {
}

variable "REVISION" {
  default = "unknown"
}

variable "CREATED" {
  default = "1970-01-01T00:00:00Z"
}

group "default" {
  targets = ["control-plane", "worker"]
}

group "mcp-release" {
  targets = ["mcp"]
}

target "mcp" {
  inherits = ["common"]
  dockerfile = "containers/mcp/Dockerfile"
  tags = ["codinglair/codinglair-taf-mcp:${VERSION}"]
  platforms = ["linux/amd64", "linux/arm64"]
}

target "common" {
  context = "."
  args = {
    VERSION = VERSION
    REVISION = REVISION
    CREATED = CREATED
  }
  platforms = ["linux/amd64"]
}

target "control-plane" {
  inherits = ["common"]
  dockerfile = "containers/control-plane/Dockerfile"
  tags = ["codinglair/taf-control-plane:${VERSION}"]
}

target "worker" {
  inherits = ["common"]
  dockerfile = "containers/worker/Dockerfile"
  tags = ["codinglair/taf-worker:${VERSION}"]
}
