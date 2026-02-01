import json
import os
from itertools import product

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))

ALL_PARTS_PATH = os.path.join(
    ROOT,
    'src', 'main', 'resources', 'Common', 'Blocks', 'Pipe_Models',
    'HyFactory_Pipe_All_Parts.blockymodel'
)
OUTPUT_DIR = os.path.join(
    ROOT,
    'src', 'main', 'resources', 'Common', 'Blocks', 'Pipe_Models'
)
ITEM_JSON_PATH = os.path.join(
    ROOT,
    'src', 'main', 'resources', 'Server', 'Item', 'Items', 'HyFactory_Item_Pipe.json'
)

BASE_MODEL_PATH = 'Blocks/HyFactory_Pipe_Base.blockymodel'
MODEL_PREFIX = 'Blocks/Pipe_Models/HyFactory_Pipe_'
DEFAULT_STATE = 'North0_South0_East0_West0_Up0_Down0'

SIDE_ORDER = [
    ('north', 'North'),
    ('south', 'South'),
    ('east', 'East'),
    ('west', 'West'),
    ('up', 'Up'),
    ('down', 'Down'),
]

STATE_VALUES = [0, 1, 2, 3]


def load_all_parts():
    with open(ALL_PARTS_PATH, 'r', encoding='utf-8') as f:
        data = json.load(f)
    nodes = data.get('nodes', [])
    by_name = {node.get('name'): node for node in nodes}
    return data, nodes, by_name


def build_state_key(values):
    parts = []
    for (_, title), value in zip(SIDE_ORDER, values):
        parts.append(f"{title}{value}")
    return '_'.join(parts)


def node_filter_names(nodes, side_title, state_value):
    names = []
    if state_value >= 1:
        names.append(f"{side_title}_Connector")
    if state_value == 2:
        names.extend([node['name'] for node in nodes if node['name'].startswith(f"{side_title}_Push_Connector_")])
    elif state_value == 3:
        names.extend([node['name'] for node in nodes if node['name'].startswith(f"{side_title}_Pull_Connector_")])
    return names


def build_nodes(nodes, by_name, values):
    selected_names = {'Base'}
    for (short, title), state_value in zip(SIDE_ORDER, values):
        for name in node_filter_names(nodes, title, state_value):
            selected_names.add(name)
    selected_nodes = [by_name[name] for name in selected_names if name in by_name]
    # Preserve original ordering for stability
    ordered = [node for node in nodes if node.get('name') in selected_names]
    return ordered


def ensure_dir(path):
    os.makedirs(path, exist_ok=True)


def write_model(state_key, nodes, template):
    output_path = os.path.join(OUTPUT_DIR, f"HyFactory_Pipe_{state_key}.blockymodel")
    payload = {
        'nodes': nodes,
        'format': template.get('format', 'prop'),
        'lod': template.get('lod', 'auto'),
    }
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(payload, f, indent=2)
        f.write('\n')


def update_item_json(state_to_model):
    with open(ITEM_JSON_PATH, 'r', encoding='utf-8') as f:
        item = json.load(f)

    block_type = item.get('BlockType', {})
    state = block_type.get('State') or {}
    state['Id'] = state.get('Id') or 'item_pipe'
    # Replace definitions to avoid stale lowercase keys lingering.
    definitions = {}

    # Replace only our generated states
    for state_key, model_path in state_to_model.items():
        definitions[state_key] = {
            'CustomModel': model_path
        }

    state['Definitions'] = definitions
    block_type['State'] = state
    item['BlockType'] = block_type

    with open(ITEM_JSON_PATH, 'w', encoding='utf-8') as f:
        json.dump(item, f, indent=2)
        f.write('\n')


def main():
    template, nodes, by_name = load_all_parts()
    ensure_dir(OUTPUT_DIR)

    state_to_model = {}

    for values in product(STATE_VALUES, repeat=len(SIDE_ORDER)):
        state_key = build_state_key(values)
        if state_key == DEFAULT_STATE:
            # Use the base model for default state to avoid duplicate file.
            state_to_model[state_key] = BASE_MODEL_PATH
            continue
        selected_nodes = build_nodes(nodes, by_name, values)
        if not selected_nodes:
            # Should not happen (Base always included)
            continue
        write_model(state_key, selected_nodes, template)
        state_to_model[state_key] = f"{MODEL_PREFIX}{state_key}.blockymodel"

    update_item_json(state_to_model)
    print(f"Generated {len(state_to_model)} state definitions.")


if __name__ == '__main__':
    main()
