# BXAgent - LLM-driven Generation of EMF Model Transformations

[![Java CI with Maven](https://github.com/tbuchmann/bx-agent/workflows/Java%20CI%20with%20Maven/badge.svg)](https://github.com/tbuchmann/bx-agent/actions/workflows/maven.yml)
[![License](https://img.shields.io/badge/license-EPL--2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Maven](https://img.shields.io/badge/build-Maven-red.svg)](https://maven.apache.org/)

A Java CLI-tool, capable of generating Java Code for a bidirectional and incremental model transformation, based on two `.ecore`-metamodels and a natural language description.

## Features

- **LLM-supported mapping extraction** - extracts transformation specifications from pairs of metamodels
- **Bidirectional transformations** - generates forward and backward transformations
- **Incremental support** - creates and maintains a correspondence model and employs fingerpring matching of model elements to propagate changes
- **Support for concurrent synchronization** - is able to handle concurrent updates to both models
- **Automatic code generation** - creates a complete Java class for the transformation using FreeMarker templates
- **Compilation validation** - validates the generated code using javac and fixes errors automatically
- **Multi-provider support** - Ollama, Anthropic Claude, OpenAI
- **Interactive mode** - solves mapping ambiguities with the help of user prompts

## Installation & Build

### Prerequisites

- Java 21+
- Maven 3.6+
- Ollama for local LLMs (optional)

### Build

```bash
mvn clean package
```

Creates a Fat-JAR: `target/bx-agent-1.0.0-SNAPSHOT.jar`

## Usage

### 1. Configuration

Create `config/agent.properties`:

```properties
# LLM Provider (ollama, anthropic, openai)
llm.provider=ollama

# Ollama Configuration
llm.base_url=http://localhost:11434
llm.model=devstral-small-2
llm.temperature=0.2
llm.timeout=120

# For Anthropic/OpenAI:
# llm.api_key=sk-...
```

### 2. Generate Transformation

```bash
java -jar target/bx-agent-1.0.0-SNAPSHOT.jar \
  --source examples/pdb/PersonsDB1.ecore \
  --target examples/pdb/PersonsDB2.ecore \
  --output-dir generated \
  --description "Map PersonsDB1 to PersonsDB2, combining firstName and lastName into name"
```

### CLI options

| Option | Short | Description | Default |
|--------|----------|--------------|---------|
| `--source` | `-s` | Path to Source .ecore Datei | (required) |
| `--target` | `-t` | Path to Target .ecore Datei | (required) |
| `--output-dir` | `-o` | Output directory | `./generated` |
| `--config` | `-c` | Config file | `config/agent.properties` |
| `--description` | `-d` | natural language description | (optional) |
| `--validate` | `--no-validate` | | activate/deactivate Code validation | `true` |
| `--interactive` | `--no-interactive` | | interactive mode | `true` |
| `--exclude` | `-e`| Exclude features from mapping | (optional) |
| `--help` | `-h` | show help | |
| `--version` | `-V` | show version | |

## Technology Stack

- **Java 21** - Pattern Matching, Sealed Interfaces, Records
- **Picocli 4.7.7** - CLI Framework
- **Eclipse EMF 2.29.0** - Ecore Parsing (standalone)
- **Langchain4j 1.11.0** - LLM-Integration
- **FreeMarker 2.3.34** - Template Engine
- **Jackson 2.21.0** - JSON Processing
- **JUnit 6.0.3** - Testing
- **Maven Shade Plugin** - Fat JAR Packaging

