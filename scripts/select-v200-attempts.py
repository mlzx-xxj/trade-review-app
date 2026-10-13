"""Use the latest attempt for each emulator, never fall back to an older green report.
Failed artifacts remain untouched; the delivery report still verifies all selected XML.
"""
import argparse
import re
import shutil
from pathlib import Path


def select(reports, screenshots, output_reports, output_screenshots):
    reports, screenshots = Path(reports), Path(screenshots)
    result = {}
    for api in (29, 35):
        choices = {}
        for folder in reports.iterdir():
            match = re.fullmatch(rf'android-test-reports-api{api}-attempt([1-9][0-9]*)', folder.name)
            if match and folder.is_dir():
                attempt = int(match[1])
                if attempt in choices:
                    raise ValueError('Duplicate attempt')
                choices[attempt] = folder
        if not choices:
            raise ValueError(f'API {api} report is missing')
        attempt = max(choices)
        image_source = screenshots / f'v200-emulator-screenshots-api{api}-attempt{attempt}'
        if not image_source.is_dir():
            raise ValueError(f'Latest API {api} attempt {attempt} has no successful screenshot artifact; no fallback')
        shutil.copytree(choices[attempt], Path(output_reports) / f'android-test-reports-api{api}')
        shutil.copytree(image_source, Path(output_screenshots) / f'v200-emulator-screenshots-api{api}')
        result[str(api)] = attempt
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for name in ['reports', 'screenshots', 'output-reports', 'output-screenshots']:
        parser.add_argument('--' + name, required=True)
    args = parser.parse_args()
    print(select(args.reports, args.screenshots, args.output_reports, args.output_screenshots))
