from ci_security import require, require_trusted, signing_environment

require_trusted()
safe, reason = signing_environment()
require(safe, reason)
print(reason)
