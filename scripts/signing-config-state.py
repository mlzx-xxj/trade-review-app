"""Reject incomplete signing Secrets without printing any credential value."""
import os

SECRET_NAMES = (
    "EXISTING_KEY_BASE64", "EXISTING_STORE_PASSWORD", "EXISTING_KEY_ALIAS", "EXISTING_KEY_PASSWORD",
)


def signing_state(environment):
    present = [bool(environment.get(name)) for name in SECRET_NAMES]
    if any(present) and not all(present):
        raise ValueError("Signing Secrets incomplete; temporary APK fallback refused")
    return "true" if all(present) else "false"


if __name__ == "__main__":
    try:
        print(signing_state(os.environ))
    except ValueError as error:
        raise SystemExit(str(error))
