#!/usr/bin/env groovy

pipeline {
  agent any

  stages {
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

    stage('Finalize builds') {
      steps {
        sh 'rm -rf frontend/dist'
      }
    }
  }
}