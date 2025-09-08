# Git Directory Strategy Plugin

Ein Jenkins Plugin für Multibranch Pipelines, das Builds basierend auf Änderungen in bestimmten Verzeichnissen filtert.

## Beschreibung

Das Plugin erweitert die Konfiguration von Multibranch Pipelines um die Möglichkeit, mehrere Verzeichnisse anzugeben und über die GitHub API zu prüfen, ob es Änderungen in diesen Verzeichnissen gab. Es unterstützt reguläre Ausdrücke für die Verzeichnisspezifikation.

## Features

- Filtert Builds basierend auf Änderungen in bestimmten Verzeichnissen
- Unterstützt reguläre Ausdrücke für Verzeichnisspezifikation
- Integriert sich in Multibranch Pipeline Konfigurationen
- Nutzt GitHub API für Änderungsprüfung
- Credentials werden sicher in Jenkins gespeichert

## Installation

1. Baue das Plugin:
   ```bash
   mvn clean package
   ```

2. Installiere das Plugin in Jenkins:
   - Gehe zu "Manage Jenkins" → "Manage Plugins" → "Advanced"
   - Lade die `.hpi` Datei aus `target/git-dir-strategy-plugin.hpi` hoch
   - Starte Jenkins neu

## Verwendung

1. Erstelle oder bearbeite eine Multibranch Pipeline
2. Unter "Branch Sources" → "Behaviors" füge "Nur bauen bei Änderungen in Verzeichnissen (Regex)" hinzu
3. Konfiguriere die Verzeichnis-Regexe (eine pro Zeile), z.B.:
   ```
   ^src/
   ^docs/
   ^config/.*\.ya?ml$
   ```
4. Optional: Setze eine Credentials-ID für GitHub API Zugriff
5. Aktiviere die gewünschten Flags für Branch- und PR-Builds

## Entwicklung

### Voraussetzungen

- JDK 17
- Maven 3.6+

### Build

```bash
mvn clean package
```

### Tests

```bash
mvn test
```

## Lizenz

MIT License
