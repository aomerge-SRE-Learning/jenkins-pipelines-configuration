#!/bin/bash

# Script para configurar webhook programáticamente
# Uso: ./setup-webhook.sh <GITHUB_TOKEN> <JENKINS_URL> [REPO_NAME] [WEBHOOK_TYPE]

# Configuración por defecto
REPO_OWNER="aomerge-SRE-Learning"
WEBHOOK_TYPE="push"  # push, pullrequest, hybrid

# Parámetros
GITHUB_TOKEN="$1"
JENKINS_URL="$2"
REPO_NAME="${3:-angular-proyect}"
WEBHOOK_TYPE="${4:-push}"

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Funciones
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Verificar parámetros
if [ -z "$GITHUB_TOKEN" ] || [ -z "$JENKINS_URL" ]; then
    log_error "Parámetros faltantes"
    echo "Uso: $0 <GITHUB_TOKEN> <JENKINS_URL> [REPO_NAME] [WEBHOOK_TYPE]"
    echo ""
    echo "Ejemplos:"
    echo "  $0 ghp_xxx... https://abc123.ngrok.io"
    echo "  $0 ghp_xxx... https://jenkins.company.com angular-proyect hybrid"
    exit 1
fi

# Configurar eventos según tipo
case $WEBHOOK_TYPE in
    push)
        EVENTS='["push"]'
        log_info "Configurando webhook para: PUSH events"
        ;;
    pullrequest)
        EVENTS='["push", "pull_request"]'
        log_info "Configurando webhook para: PUSH + PULL REQUEST events"
        ;;
    hybrid)
        EVENTS='["push", "pull_request", "create", "delete"]'
        log_info "Configurando webhook para: HYBRID events (push, PR, branches)"
        ;;
    *)
        EVENTS='["push"]'
        log_warn "Tipo desconocido '$WEBHOOK_TYPE', usando 'push' por defecto"
        ;;
esac

# Verificar si Jenkins está accesible
log_info "Verificando accesibilidad de Jenkins..."
if ! curl -s --max-time 10 "$JENKINS_URL" > /dev/null; then
    log_error "Jenkins no es accesible en: $JENKINS_URL"
    log_warn "Asegúrate de que Jenkins esté corriendo y accesible desde internet"
    exit 1
fi

log_info "✅ Jenkins accesible en: $JENKINS_URL"

# Crear webhook usando API de GitHub
log_info "Creando webhook para: $REPO_OWNER/$REPO_NAME"

RESPONSE=$(curl -s -w "%{http_code}" -X POST \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  -H "Content-Type: application/json" \
  "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/hooks" \
  -d "{
    \"name\": \"web\",
    \"active\": true,
    \"events\": $EVENTS,
    \"config\": {
      \"url\": \"$JENKINS_URL/github-webhook/\",
      \"content_type\": \"json\",
      \"insecure_ssl\": \"0\"
    }
  }")

HTTP_CODE="${RESPONSE: -3}"
RESPONSE_BODY="${RESPONSE%???}"

case $HTTP_CODE in
    201)
        log_info "✅ Webhook creado exitosamente"
        log_info "📍 URL: $JENKINS_URL/github-webhook/"
        log_info "📊 Eventos: $WEBHOOK_TYPE"
        echo ""
        log_info "Para verificar el webhook:"
        log_info "  GitHub → $REPO_OWNER/$REPO_NAME → Settings → Webhooks"
        ;;
    422)
        log_warn "⚠️  Webhook ya existe o configuración inválida"
        echo "Respuesta: $RESPONSE_BODY" | jq '.' 2>/dev/null || echo "$RESPONSE_BODY"
        ;;
    401|403)
        log_error "❌ Error de autenticación o permisos"
        log_error "Verifica que el token tenga permisos de 'admin:repo_hook'"
        ;;
    404)
        log_error "❌ Repositorio no encontrado: $REPO_OWNER/$REPO_NAME"
        log_error "Verifica el nombre del repositorio y los permisos"
        ;;
    *)
        log_error "❌ Error HTTP $HTTP_CODE"
        echo "Respuesta: $RESPONSE_BODY"
        ;;
esac

# Instrucciones adicionales
echo ""
log_info "📋 Próximos pasos:"
log_info "1. Verifica el webhook en GitHub → Settings → Webhooks"
log_info "2. Haz un push de prueba: git commit -am 'test webhook' && git push"
log_info "3. Verifica en Jenkins que el build se dispare automáticamente"