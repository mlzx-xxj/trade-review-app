import importlib.util
import itertools
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("signing_config", Path(__file__).parents[1] / "signing-config-state.py")
config = importlib.util.module_from_spec(spec)
spec.loader.exec_module(config)


class SigningConfigurationTest(unittest.TestCase):
    def test_no_credentials_are_explicitly_temporary(self):
        self.assertEqual("false", config.signing_state({}))

    def test_complete_credentials_enable_fixed_signing(self):
        self.assertEqual("true", config.signing_state(dict.fromkeys(config.SECRET_NAMES, "synthetic-test-value")))

    def test_every_partial_configuration_is_rejected(self):
        for count in range(1, 4):
            for subset in itertools.combinations(config.SECRET_NAMES, count):
                with self.subTest(names=subset), self.assertRaises(ValueError):
                    config.signing_state(dict.fromkeys(subset, "synthetic-test-value"))


if __name__ == "__main__":
    unittest.main()
