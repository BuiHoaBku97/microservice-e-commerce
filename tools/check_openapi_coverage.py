"""Fail when a controller operation is absent from the checked-in OpenAPI document."""

from pathlib import Path
import re
import sys
import yaml


ROOT = Path(__file__).resolve().parents[1]
METHODS = {"Get": "get", "Post": "post", "Put": "put", "Patch": "patch", "Delete": "delete"}


def normalize(path: str) -> str:
    path = re.sub(r"//+", "/", path)
    return path if path.startswith("/") else "/" + path


implemented = set()
for service in ("identity-service", "catalog-service", "order-service", "payment-service", "notification-service"):
    for source in (ROOT / service / "src/main/java").rglob("*Controller.java"):
        text = source.read_text(encoding="utf-8")
        class_at = text.find("class ")
        header = text[:class_at]
        base_matches = re.findall(r'@RequestMapping\("([^"]+)"\)', header)
        base = base_matches[-1] if base_matches else ""
        pattern = r'@(Get|Post|Put|Patch|Delete)Mapping(?:\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\))?'
        for verb, suffix in re.findall(pattern, text[class_at:]):
            implemented.add((METHODS[verb], normalize(base + suffix)))

document = yaml.safe_load((ROOT / "docs/openapi.yaml").read_text(encoding="utf-8"))
documented = {
    (method.lower(), path)
    for path, item in document["paths"].items()
    for method in item
    if method.lower() in METHODS.values()
}

missing = sorted(implemented - documented)
if missing:
    print("Controller operations missing from OpenAPI:")
    for method, path in missing:
        print(f"  {method.upper()} {path}")
    sys.exit(1)

operation_ids = [operation["operationId"] for item in document["paths"].values() for operation in item.values()]
if len(operation_ids) != len(set(operation_ids)):
    print("OpenAPI operationId values must be unique")
    sys.exit(1)

print(f"OpenAPI covers all {len(implemented)} controller operations; {len(documented)} operations documented.")
