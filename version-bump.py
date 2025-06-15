#!/usr/bin/env python3

import argparse
import xml.etree.ElementTree as ET

def __bump(filename, old, new, preserve=None):
    lines = []
    with open(filename, 'r') as f:
        for line in f:
            if preserve and not preserve(line.rstrip()):
                line = line.replace(old, new)
            lines.append(line)
    with open(filename, 'w') as f:
        for line in lines:
            f.write(line)

def __bump_xhtml(filename, old, new):
    __bump(filename, old, new, preserve=lambda s: s.endswith('<!-- hold-version -->'))

def __bump_yaml(filename, old, new):
    __bump(filename, old, new, preserve=lambda s: s.endswith('# hold-version'))

def pom(old, new):
    __bump_xhtml('pom.xml', old, new)

def readme(old, new):
    __bump_xhtml('README.md', old, new)

def docker_compose(old, new):
    __bump_yaml('docker-compose.yml', old, new)
    __bump_yaml('docker-compose.cluster.yml', old, new)

def helm(old, new):
    __bump_yaml('chart/Chart.yaml', old, new)
    __bump_xhtml('chart/README.md', old, new)

parser = argparse.ArgumentParser(description='Version bumper')

pom_tree = ET.parse('pom.xml')
pom_version = pom_tree.find('{http://maven.apache.org/POM/4.0.0}version').text

parser.add_argument('--old', help='Old version. Defaults to parse from pom.xml version field', default=pom_version)
parser.add_argument('--new', help='New Version')
args = parser.parse_args()

if not args.new:
    parser.print_help()
    raise ValueError('missing new version argument')

for f in [pom, readme, docker_compose, helm]:
    f(args.old, args.new)
