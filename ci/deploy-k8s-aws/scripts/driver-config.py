#!/usr/bin/python3

import os
import sys
import yaml


def append_to_container(config, driver, spec):
    for entry in config[driver]:
        spec['spec']['template']['spec']['containers'][0][entry] = config[driver][entry]
    return spec


def add_config(config):
    for driver in config:
        spec_list = []
        with open('./deploy/deployment-' + driver + '.yaml', 'r+') as spec_file:
            for spec in yaml.load_all(spec_file, Loader=yaml.FullLoader):

                if spec['kind'] == 'Deployment':
                    changed_spec = append_to_container(config, driver, spec)
                    print(changed_spec)
                    spec_list.append(spec)
                else:
                    spec_list.append(spec)

            spec_file.close()

        with open('./deploy/deployment-' + driver + '.yaml', 'w') as spec_file:
            yaml.dump_all(spec_list, spec_file, default_flow_style=False)
            spec_file.close()


# Fix Issue #569
def safe_yaml_load(stream, allowed_keys):
    data = yaml.load(stream, Loader=yaml.FullLoader)
    if not isinstance(data, dict):
        raise ValueError("Loaded YAML is not a dictionary.")
    for key in data.keys():
        if key not in allowed_keys:
            raise ValueError(f"Unexpected key '{key}' found in YAML. Allowed keys: {allowed_keys}")
    return data


def main(argv):
    allowed_keys = ['driver1', 'driver2', 'driver3']  # 화이트리스트에 허용할 키값을 명시적으로 작성
    with open('driver-config.yaml', 'r') as f:
        config = safe_yaml_load(f, allowed_keys)
    add_config(config)


if __name__ == "__main__":
    main(sys.argv[1:])
    print('%s script finished' % os.path.basename(__file__))
