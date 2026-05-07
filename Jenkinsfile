#!/usr/bin/env groovy

pipeline {
  agent any

  stages {
    stage("Initialization") {
      steps {
        script {
          def version = sh(returnStdout: true, script: 'cd backend && docker compose run --rm maven mvn -Duser.home=/var/maven help:evaluate -Dexpression=project.version -q -DforceStdout')
          buildName "${env.GIT_BRANCH.replace("origin/", "")}@${version}"
        }
      }
    }
    stage('Build frontend') {
      steps {
        dir('frontend') {
          sh './build.sh clean init build'
        }
      }
    }

    stage('Copy front files') {
      steps {
        sh './copyFrontFiles.sh'
      }
    }

    stage('Build backend') {
      steps {
        dir('backend') {
            sh './build.sh clean build publish'
        }
      }
    }
    stage('Build image') {
        steps {
          dir('backend') {
            sh './edifice image --rebuild=false'
          }
        }
    }

    stage('Finalize builds') {
      steps {
        sh 'rm -rf frontend/dist'
        sh 'cd backend && docker compose down'
      }
    }
  }
}