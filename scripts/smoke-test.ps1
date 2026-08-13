$ErrorActionPreference = 'Stop'

$baseUrl = if ($env:COURSE_PLATFORM_URL) { $env:COURSE_PLATFORM_URL.TrimEnd('/') } else { 'http://localhost:8080' }
$passes = 0

function Pass([string]$message) {
    $script:passes++
    Write-Host "[$script:passes/8] PASS $message"
}

function Headers([string]$token) {
    return @{ Authorization = "Bearer $token" }
}

function Wait-Until([scriptblock]$operation, [scriptblock]$condition, [string]$failure) {
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try {
            $result = & $operation
            if (& $condition $result) {
                return $result
            }
        } catch {
        }
        Start-Sleep -Seconds 1
    }
    throw $failure
}

$health = Invoke-RestMethod "$baseUrl/actuator/health/readiness"
if ($health.status -ne 'UP') { throw 'Application readiness is not UP' }
Pass 'application is ready'

$adminLogin = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/auth/login" -ContentType 'application/json' -Body (@{
    email = 'admin@example.test'
    password = 'password'
} | ConvertTo-Json)
$adminToken = $adminLogin.token
if (-not $adminToken) { throw 'Admin token was not returned' }
Pass 'admin authenticated'

$suffix = [guid]::NewGuid().ToString('N')
$category = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/categories" -Headers (Headers $adminToken) -ContentType 'application/json' -Body (@{
    name = "Smoke $suffix"
    description = 'Docker smoke test'
} | ConvertTo-Json)
$course = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/courses" -Headers (Headers $adminToken) -ContentType 'application/json' -Body (@{
    title = "Event-driven smoke $suffix"
    description = 'End-to-end Docker verification'
    estimatedHours = 4
    level = 'INTERMEDIATE'
    price = 49.90
    currency = 'EUR'
    capacity = 2
    categoryId = $category.id
    instructorId = '20000000-0000-0000-0000-000000000002'
} | ConvertTo-Json)
Pass 'catalog resources created'

$published = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/courses/$($course.id)/publish" -Headers (Headers $adminToken)
if ($published.status -ne 'PUBLISHED') { throw 'Course was not published' }
Pass 'course published'

$studentLogin = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/auth/login" -ContentType 'application/json' -Body (@{
    email = 'student@example.test'
    password = 'password'
} | ConvertTo-Json)
$studentToken = $studentLogin.token
if (-not $studentToken) { throw 'Student token was not returned' }
Pass 'student authenticated'

$enrollment = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/courses/$($course.id)/enrollments" -Headers (@{
    Authorization = "Bearer $studentToken"
    'Idempotency-Key' = "smoke-$suffix"
    'X-Correlation-Id' = [guid]::NewGuid().ToString()
})
$active = Wait-Until {
    Invoke-RestMethod -Uri "$baseUrl/api/v1/enrollments/$($enrollment.enrollmentId)" -Headers (Headers $studentToken)
} { param($value) $value.status -eq 'ACTIVE' } 'Enrollment did not become ACTIVE'
Pass 'asynchronous payment activated enrollment'

$completed = Invoke-RestMethod -Method Patch -Uri "$baseUrl/api/v1/enrollments/$($active.id)/progress" -Headers (Headers $studentToken) -ContentType 'application/json' -Body '{"progress":100}'
if (-not $completed.completedNow) { throw 'Enrollment was not completed' }
Pass 'enrollment completed'

$certificate = Wait-Until {
    Invoke-RestMethod -Uri "$baseUrl/api/v1/certificates/enrollment/$($active.id)" -Headers (Headers $studentToken)
} { param($value) -not [string]::IsNullOrWhiteSpace($value.verificationCode) } 'Certificate was not issued'
$verified = Invoke-RestMethod -Uri "$baseUrl/api/v1/certificates/verify/$($certificate.verificationCode)"
if ($verified.enrollmentId -ne $active.id) { throw 'Certificate verification returned another enrollment' }
Pass 'certificate issued and publicly verified'

Write-Host '8/8 PASS'

