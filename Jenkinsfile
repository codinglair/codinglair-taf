pipeline {
    agent none
    options {
        timestamps()
        disableConcurrentBuilds(abortPrevious: false)
        timeout(time: 4, unit: 'HOURS')
    }
    environment {
        MAVEN_ARGS = '-B -ntp -Dstyle.color=always'
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository'
    }
    stages {
        stage('Secret scanning') {
            agent { label 'linux && docker' }
            steps {
                checkout scm
                sh 'sh build-support/scripts/run-gitleaks.sh history'
            }
        }
        stage('Runtime, compatibility, and architecture') {
            agent { label 'linux && java25' }
            steps {
                checkout scm
                sh 'java build-support/scripts/SyncDocVersion.java --check'
                sh './mvnw --version'
                sh './mvnw $MAVEN_ARGS -f codinglair-taf-runtime/pom.xml clean verify -Pruntime-gate'
                sh './mvnw $MAVEN_ARGS -Pdependency-analysis,architecture,api-compatibility,schema-compatibility verify'
            }
            post { always { archiveArtifacts artifacts: '**/target/*-reports/**', allowEmptyArchive: true } }
        }
        stage('Browser compatibility matrix') {
            agent { label 'linux && java25' }
            steps {
                checkout scm
                sh './mvnw $MAVEN_ARGS -pl codinglair-taf-runtime/taf-web-playwright -am test-compile dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt'
                sh 'java -cp "$(cat codinglair-taf-runtime/taf-web-playwright/target/test-classpath.txt)" com.microsoft.playwright.CLI install --with-deps chromium firefox webkit'
                sh './mvnw $MAVEN_ARGS -pl codinglair-taf-runtime/taf-web-playwright -am -Dtaf.browser.smoke=true -Dtest=PlaywrightBrowserSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test'
            }
            post { always { archiveArtifacts artifacts: '**/target/*-reports/**', allowEmptyArchive: true } }
        }
        stage('Container suites') {
            agent { label 'linux && java25 && docker' }
            steps {
                checkout scm
                sh '''
                    set -eu
                    mkdir -p target/verification
                    docker ps -aq | sort > target/verification/containers-before.txt
                    docker network ls -q | sort > target/verification/networks-before.txt
                    for module in codinglair-taf-runtime/taf-environments codinglair-taf-runtime/taf-test-definitions-mongodb codinglair-taf-runtime/taf-data-migration codinglair-taf-runtime/taf-messaging-kafka codinglair-taf-runtime/taf-messaging-rabbitmq codinglair-taf-runtime/taf-messaging-jms codinglair-taf-runtime/taf-virtualization-wiremock
                    do
                      ./mvnw $MAVEN_ARGS -pl "$module" -am verify -Pcontainers
                    done
                '''
            }
            post {
                always {
                    sh '''
                        docker ps -aq | sort > target/verification/containers-after.txt
                        docker network ls -q | sort > target/verification/networks-after.txt
                        comm -13 target/verification/containers-before.txt target/verification/containers-after.txt > target/verification/residual-containers.txt
                        comm -13 target/verification/networks-before.txt target/verification/networks-after.txt > target/verification/residual-networks.txt
                        test ! -s target/verification/residual-containers.txt
                        test ! -s target/verification/residual-networks.txt
                    '''
                    archiveArtifacts artifacts: 'target/verification/**, **/target/*-reports/**', allowEmptyArchive: true
                }
            }
        }
        stage('MCP, security, cancellation, and cleanup') {
            agent { label 'linux && java25' }
            steps {
                checkout scm
                sh './mvnw $MAVEN_ARGS -f taf-mcp-server/pom.xml clean verify -Pmcp-e2e,security-it'
            }
            post { always { archiveArtifacts artifacts: '**/target/*-reports/**', allowEmptyArchive: true } }
        }
        stage('Android emulator Appium and cleanup') {
            agent { label 'linux && java25 && docker && kvm && pwsh' }
            steps {
                checkout scm
                sh 'test -c /dev/kvm && test -r /dev/kvm && test -w /dev/kvm'
                pwsh 'containers/android-emulator/google/build.ps1 -AcceptAndroidSdkLicense -Clean'
                pwsh 'containers/android-emulator/google/verify.ps1'
                pwsh 'containers/android-emulator/google/verify-compose.ps1'
                pwsh 'containers/android-emulator/google/qualify.ps1 -Runs 2'
                pwsh 'containers/android-emulator/google/qualify.ps1 -Runs 1 -ControlledFailure'
            }
            post {
                always {
                    sh 'docker compose --file containers/android-emulator/google/compose.qualify.yaml down --remove-orphans'
                    archiveArtifacts artifacts: 'target/mob-003/**, codinglair-taf-runtime/taf-mobile-appium/target/surefire-reports/**', allowEmptyArchive: true
                }
            }
        }
    }
}
