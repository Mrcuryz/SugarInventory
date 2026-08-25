param(
    [string]$BaseUrl = "http://127.0.0.1:38082",

    [Parameter(Mandatory = $true)]
    [string]$LoginName,

    [Parameter(Mandatory = $true)]
    [string]$LoginPassword,

    [string]$Question = "白砂糖金属检测限值是什么？"
)

$ErrorActionPreference = 'Stop'
$uri = [Uri]$BaseUrl
if ($uri.Scheme -ne 'http' -or $uri.Host -notin @('127.0.0.1', 'localhost')) {
    throw "Refusing non-local Agent host: $BaseUrl"
}

function Invoke-AgentApi {
    param(
        [ValidateSet('Post', 'Delete')]
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [string]$Token
    )

    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers.Authorization = "Bearer $Token"
    }
    $parameters = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
        ContentType = 'application/json; charset=utf-8'
        TimeoutSec = 90
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }
    Invoke-RestMethod @parameters
}

$sessionId = $null
$token = $null
try {
    $login = Invoke-AgentApi Post '/api/auth/web-login' @{
        name = $LoginName
        password = $LoginPassword
    } $null
    $token = $login.data.token
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw 'Web login did not return a token'
    }

    $session = Invoke-AgentApi Post '/api/agent/sessions' @{
        clientType = 'RAG_RUNTIME_ACCEPTANCE'
        requestedScopes = @('mcp:warehouse:read')
        mcpTransport = 'STDIO'
    } $token
    $sessionId = $session.data.agentSessionId
    if ([string]::IsNullOrWhiteSpace($sessionId)) {
        throw 'Agent session creation did not return an agentSessionId'
    }

    $response = Invoke-AgentApi Post "/api/agent/sessions/$sessionId/messages" @{
        message = $Question
        pageContext = @{}
    } $token
    $answer = [string]$response.data.answer
    $knowledgeCards = @($response.data.cards | Where-Object { $_.cardType -eq 'knowledge_evidence' })

    if ([string]::IsNullOrWhiteSpace($answer)) {
        throw 'Knowledge query returned an empty answer'
    }
    if ($answer -match '知识库当前不可用|RAG_UNAVAILABLE') {
        throw 'Knowledge query fell back to the unavailable response'
    }
    if ($knowledgeCards.Count -lt 1) {
        throw 'Knowledge query did not return a safe knowledge_evidence card'
    }

    [pscustomobject]@{
        Result = 'PASS'
        SessionCreated = $true
        KnowledgeEvidenceCards = $knowledgeCards.Count
        AnswerLength = $answer.Length
        InternalToolNamesExposed = @($response.data.toolCalls).Count -gt 0
    } | ConvertTo-Json -Compress
} finally {
    if (-not [string]::IsNullOrWhiteSpace($sessionId) -and -not [string]::IsNullOrWhiteSpace($token)) {
        try {
            Invoke-AgentApi Delete "/api/agent/sessions/$sessionId" @{
                revokedReason = 'RAG_RUNTIME_ACCEPTANCE_COMPLETED'
            } $token | Out-Null
        } catch {
            Write-Warning 'Temporary Agent session cleanup failed; inspect the session registry.'
        }
    }
}
