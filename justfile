build:
    ./gradlew :phone:assembleDebug :wear:assembleDebug

test:
    ./gradlew :shared:jvmTest

format:
    ./gradlew ktfmtFormat

lint:
    ./gradlew detekt :phone:lintDebug :wear:lintDebug :platform:lintDebug

precommit:
    ./gradlew precommit
