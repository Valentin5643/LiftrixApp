@echo off
echo Running ErrorMappingExtensionsTest...
call gradlew test --tests "*ErrorMappingExtensionsTest" --continue --info > error-mapping-test-output.txt 2>&1
echo Test output saved to error-mapping-test-output.txt
type error-mapping-test-output.txt