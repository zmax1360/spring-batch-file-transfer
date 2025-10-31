## Spring Batch: GZ file → Postgres lookup → Oracle insert

### What it does
- Reads line-by-line data from `.gz` files in archive directories (`.../log/report/instance_#/report/archive/`)
- Only processes files containing "nomatch" keyword in filename
- Parses each line using a configurable pattern (delimiter, field names, types)
- For **entity** service: looks up rows in `dfdr_entity` on Postgres source DB and inserts to Oracle destination
- For **payload** service: checks destination DB first, performs calculations, then inserts parsed fields
- Tracks processed files using Spring Batch's ExecutionContext (persisted via JobRepository)

### Configure
Edit `src/main/resources/application.yml`:

**Required:**
- `app.archiveDir`: base directory (searches recursively for `.gz` files in `.../report/archive/` subfolders)
- `app.tableName`: table name (typically `dfdr_entity`)
- `app.idColumn`: the identifier column in the database table
- `source.datasource`: Postgres JDBC connection settings
- `destination.datasource`: Oracle JDBC connection settings

**File processing:**
- `app.pattern`: line parsing configuration
  - `delimiter`: field separator (e.g., `,`)
  - `fields`: array of field definitions with `name` and `type` (string, int, long, etc.)
  - `idField`: field name in the line that contains the entity ID
  - `serviceNameField`: field name containing service name (or use `serviceNameMode` for regex extraction)
  - `serviceNameMode`: `FIELD` | `LINE_REGEX` | `FILE_NAME_REGEX`
  - `serviceNameRegex`: regex pattern when using regex modes (first capturing group)

**Optional:**
- `app.moveProcessedTo`: folder to move processed `.gz` files after reading (blank to skip)
- `app.fileGlob`: file pattern filter (defaults to `*.gz`)
- `app.softInsert`: if `true`, all destination writes are rolled back (for testing/dry-run)

### Service routing

The application routes processing based on `serviceName` extracted from each line:

- **`entity`**: Queries Postgres `dfdr_entity` table by ID field, then inserts the full DB row into Oracle
- **`payload`**: Checks if record exists in destination, performs any calculations, then inserts parsed line fields

To add custom routing logic, modify `PayloadItemWriter` or extend `RoutingItemWriter` in `BatchConfig`.

### File tracking

Processed files are automatically tracked using Spring Batch's `ExecutionContext`, which is persisted via `JobRepository`:
- Files are tracked by full path
- On restart/resume, already-processed files are automatically skipped
- No separate database needed - uses Spring Batch's built-in metadata tables

### Build and run
```bash
mvn clean package
java -jar target/spring-batch-file-transfer-0.0.1-SNAPSHOT.jar
```

The job name is `transferJob` and runs on startup. Set `spring.batch.job.enabled=false` to disable auto-run and launch with parameters if desired.

### Example configuration

```yaml
app:
  archiveDir: "/data/log/report"
  tableName: "dfdr_entity"
  idColumn: "id"
  softInsert: false
  pattern:
    delimiter: ","
    serviceNameField: "serviceName"
    serviceNameMode: "FIELD"
    idField: "id"
    fields:
      - { name: "datetime", type: "string" }
      - { name: "serviceName", type: "string" }
      - { name: "id", type: "string" }
      - { name: "message", type: "string" }
```

### Notes
- File filtering: Only `.gz` files containing "nomatch" in the filename, located under `.../report/archive/` folders are processed
- The writer generates an `INSERT` using all columns returned by the source query (entity) or parsed fields (payload). Ensure the destination table has matching columns/types
- If you need upsert/merge semantics, adjust `EntityMapItemWriter` or `PayloadItemWriter` to use `MERGE` with your key columns
- For multi-threading and partitioning (handling 10M+ files), consider implementing a `Partitioner` to split work by `instance_*` directories


