# Demo Script

本文件提供完整的專案展示流程，可用於 Demo 或自我演練。  

---  

## 一、Demo 目標

本 Demo 將展示以下能力：  

1. 微服務架構（service-a / service-b）  
2. Jenkins CI/CD 自動部署  
3. Selective Deployment（只部署異動服務）  
4. AWS ECS Fargate 部署與 ALB 路由  
5. 基本 Observability（Logs / traceId）  

---  

## 二、前置條件

- 專案已部署於 AWS  
- Jenkins Pipeline 正常運作  
- ALB HTTPS endpoint 可存取  

---  

## 三、Demo 流程

---  

### Step 1：確認服務正常

```bash  
curl https://your-domain/api/a/health
curl https://your-domain/api/b/health
```

👉 預期結果：

```
{"status":"UP"}
```

---

### Step 2：確認目前版本

```
curl https://your-domain/api/a
```

👉 範例回應：

```
service-a v1
```

---

### Step 3：修改 service-a 程式碼

例如修改 Controller：

```
return "service-a v2";
```

---

### Step 4：push 到 GitHub

```
git commit -am "update service-a version"  
git push
```

---

### Step 5：觀察 Jenkins Pipeline

- Pipeline 自動觸發
- 執行：
  - build
  - docker build
  - trivy scan
  - push ECR
  - deploy ECS
  - ZAP scan

👉 重點觀察：

- **只 deploy service-a（Selective Deployment）**

---

### Step 6：驗證部署結果

```
curl https://your-domain/api/a
```

👉 預期：

```
service-a v2
```

---

```
curl https://your-domain/api/b
```

👉 預期：

```
service-b v1
```

👉 重點：

- service-b 沒有被影響

---

### Step 7：查看 Logs（Observability）

在 CloudWatch Logs 中：

- 查詢 service-a logs
- 可看到：
  - JSON logging
  - traceId（Correlation ID）

👉 若有 service-to-service 呼叫：

- 可追蹤 request flow

---

## 四、Demo 重點說明（面試要講）

在 Demo 過程中，建議強調：

### 1️⃣ 微服務隔離

- service-a / service-b 獨立部署
- 修改一個服務不影響其他服務

---

### 2️⃣ CI/CD 自動化

- GitHub push → Jenkins → ECS deployment
- 全流程自動化

---

### 3️⃣ Selective Deployment（亮點）

- 使用 git diff 判斷變更
- 只部署異動服務
- 提升效率、降低風險

---

### 4️⃣ DevSecOps 整合

- Trivy（Container Scan）
- ZAP（API Scan）
- non-blocking pipeline

---

### 5️⃣ Observability

- CloudWatch Logs
- JSON logging
- Correlation ID（traceId）

---

## 五、延伸說明（可選）

若時間允許，可補充：

- ECS Auto Scaling（壓測 scale out）
- Secrets Manager（避免明碼密碼）
- Rolling Deployment（不中斷服務）

---

## 六、Demo 成功標準

完成以下即代表 Demo 成功：

- service-a 成功升級版本
- service-b 未受影響
- Jenkins pipeline 成功
- ECS deployment 正常
- API 回應正確
