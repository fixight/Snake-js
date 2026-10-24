#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/host
c++ -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined -g tests/mapping_test.cpp -o build/host/mapping-test
build/host/mapping-test
