import argparse, xml.etree.ElementTree as ET

def bump(filename, old, new, preserve=None):
    lines = []
    with open(filename, 'r') as f:
        for line in f:
            if preserve and not preserve(line.rstrip()):
                line = line.replace(old, new)
            lines.append(line)
    with open(filename, 'w') as f:
        for line in lines:
            f.write(line)

def pom(old, new, preserve=lambda s: s.endswith('<!-- hold-version -->')):
    bump('pom.xml', old, new, preserve)

def readme(old, new, preserve=lambda s: s.endswith('<!-- hold-version -->')):
    bump('README.md', old, new, preserve)

def docker_compose(old, new, preserve=lambda s: s.endswith('# hold-version')):
    bump('docker-compose.yml', old, new, preserve)
    bump('docker-compose.cluster.yml', old, new, preserve)

 def helm(old, new, preserve: lambda s: s.endswith('# hold-version'):
    bump('chart/kafka-connect/Chart.yaml', old, new, preserve)

parser = argparse.ArgumentParser(description='Version bumper')

pom_tree = ET.parse('pom.xml')
pom_version = pom_tree.find('{http://maven.apache.org/POM/4.0.0}version').text

parser.add_argument('--old', help='Old version. Defaults to parse from pom.xml version field', default=pom_version)
parser.add_argument('--new', help='New Version')
args = parser.parse_args()

if not args.new:
    raise ValueError('missing new version argument')

for f in [pom, readme, docker_compose]:
    f(args.old, args.new)
