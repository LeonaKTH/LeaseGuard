package com.drone.rental;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.util.*;
class CiConfigG4Test {
 @Test void workflowParsesAndRequiresRealMysqlAndFullChecks()throws Exception{
  Map<?,?> workflow=new Yaml().load(Files.readString(Path.of("../.github/workflows/ci.yml")));
  assertEquals(Map.of("contents","read"),workflow.get("permissions"));var job=(Map<?,?>)((Map<?,?>)workflow.get("jobs")).get("verify");
  String service=Files.readString(Path.of("../scripts/ci/service.py"));assertTrue(service.contains("mysql:8.4.13"));assertTrue(service.contains("127.0.0.1:13308:3306"));assertTrue(service.contains("secrets.token_hex(32)"));assertTrue(service.contains("::add-mask::"));assertTrue(service.contains("Refuse another job container"));
  var steps=(List<?>)job.get("steps");String commands=steps.stream().map(Object::toString).collect(java.util.stream.Collectors.joining("\n"));for(String required:List.of("java-version=21","node-version=24.13.0","mvn -B -ntp verify","npm ci --ignore-scripts","npm run typecheck","npm run build","npm run test:commands","CI_MYSQL_ROOT_PASSWORD","scripts/ci/browser.sh"))assertTrue(commands.contains(required),required);
  assertFalse(commands.contains("continue-on-error"));assertFalse(commands.contains("-DskipITs"));assertFalse(commands.contains("-DskipTests verify"));assertTrue(commands.contains("inputs.browser"));
  assertTrue(commands.contains("scripts/ci/service.py start"));assertTrue(commands.contains("scripts/ci/service.py stop"));assertFalse(workflow.toString().contains("pull_request_target"));
 }
 @Test void ciScriptsRefuseExistingDatabaseAndKeepCredentialsOutOfArguments()throws Exception{
  String prepare=Files.readString(Path.of("../scripts/ci/prepare.py"));assertTrue(prepare.contains("GITHUB_ACTIONS"));assertTrue(prepare.contains("Never reset a preexisting catalog"));assertTrue(prepare.contains("SELECT,INSERT,UPDATE,DELETE ON"));assertTrue(prepare.contains("::add-mask::"));assertFalse(prepare.contains("DROP "));assertFalse(prepare.contains("TRUNCATE "));
  String browser=Files.readString(Path.of("../scripts/ci/browser.sh"));assertTrue(browser.contains("set -euo pipefail"));assertTrue(browser.contains("trap "));assertTrue(browser.contains("LEASEGUARD_G4_SNAPSHOT_TOOL"));assertTrue(browser.contains("node browser-tests/g4.mjs"));
 }
}
