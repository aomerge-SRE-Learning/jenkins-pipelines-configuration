# Jenkins Shared Library - CI/CD Templates (v2)

[![Version](https://img.shields.io/badge/version-2.0-blue.svg)](https://github.com/aomerge-SRE-Learning/jenkins-pipelines-configuration)

Esta es una biblioteca compartida de Jenkins con pipelines modulares y reutilizables siguiendo **arquitectura POO**.

## 🚀 Inicio Rápido

```groovy
@Library('jenkins-pipelines-configuration') _

CITemplate(
    serviceName: 'my-app',
    language: 'angular',
    dockerRegistry: 'docker.io/mycompany',
    dockerPush: true,
    dockerCredentialsId: 'docker-credentials'
)
```

Ver documentación completa en [README-LIBRARY.md](README-LIBRARY.md)

## 📁 Estructura

- `vars/` - Wrappers públicos
- `src/org/example/` - Lógica POO (builders, deployers, utils)
- `resources/` - Archivos de configuración (Dockerfile, K8s manifests)

## ✨ Características v2

✅ Arquitectura POO testeable  
✅ Autenticación Docker/K8s  
✅ Rollback automático  
✅ Pattern Factory para builders  
✅ Validaciones pre-deployment  

## 🎯 Lenguajes: Angular, Java, Python, Node.js

Ver más: [README-LIBRARY.md](README-LIBRARY.md)
