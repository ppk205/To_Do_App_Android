# SonarQube / SonarCloud setup

CI already contains a `sonar` job (`.github/workflows/ci.yml`). It is advisory only.
It never blocks a merge until you remove `continue-on-error: true`.

## 1. Create the project

- SonarCloud: import `ppk205/To_Do_App_Android`. Note the organization key and project key.
- SonarQube Server: create a project manually. Note the project key and the server URL.

## 2. Point the repo at it

- Edit `sonar-project.properties`: replace `sonar.projectKey` and `sonar.organization`
  with the real values.
- Self-hosted server only: delete the `sonar.organization` line (servers ignore it,
  Cloud requires it).

## 3. Add secrets

Settings → Secrets and variables → Actions:

| Secret | Required | Value |
|---|---|---|
| `SONAR_TOKEN` | always | project token from SonarCloud / Server |
| `SONAR_HOST_URL` | server only | e.g. `https://sonar.example.com` (leave unset for SonarCloud) |

The `sonar` job is skipped while `SONAR_TOKEN` is empty.

## 4. Verify

1. Push any commit to `ci/setup-pipeline` (or open the PR).
2. Tab Actions → select the run → job `sonar` is green.
3. Open the SonarCloud / Server dashboard → project shows Kotlin + JavaScript analysis.

## 5. Enforce later (optional)

1. In `.github/workflows/ci.yml`, job `sonar`: delete the `continue-on-error: true` line.
2. On GitHub: Settings → Branches → protection rule → add `sonar` to required status checks.
3. Push and confirm a failing Quality Gate blocks the merge.
