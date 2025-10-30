## Spring Batch: GZ file → Postgres lookup → Oracle insert

### What it does
- Reads line-by-line IDs from `.gz` files in an archive directory
- For each ID, looks up the row in `dfdr_entity` on the Postgres source DB
- If found, inserts that row into the same table name on the Oracle destination DB

### Configure
Edit `src/main/resources/application.yml`:
- `app.archiveDir`: folder containing `.gz` files
- `app.tableName`: typically `dfdr_entity`
- `app.idColumn`: the identifier column (each line in file should be an ID)
- `source.datasource`: Postgres JDBC settings
- `destination.datasource`: Oracle JDBC settings

Optional:
- `app.moveProcessedTo`: folder to move processed `.gz` files (blank to skip)
- `app.fileGlob`: defaults to `*.gz`

### Build and run
```bash
mvn -q -DskipTests package
java -jar target/spring-batch-file-transfer-0.0.1-SNAPSHOT.jar
```

The job name is `transferJob` and runs on startup. Set `spring.batch.job.enabled=false` to disable auto-run and launch with parameters if desired.

### Notes
- The writer generates an `INSERT` using all columns returned by the source query. Ensure the destination table has matching columns/types.
- If you need upsert/merge semantics, adjust the `OracleMapItemWriter` to use `MERGE` with your key columns.


