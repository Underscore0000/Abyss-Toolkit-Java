# ⚡ ABYSS Toolkit

<div align="center">

<h1>ABYSS Toolkit</h1>

<p>
<b>Advanced Security Assessment & Network Analysis Platform</b>
</p>

<p>
A modern all-in-one toolkit for network reconnaissance, web security analysis, OSINT investigations, vulnerability discovery and automated reporting.
</p>

<br>

<img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk"/>
<img src="https://img.shields.io/badge/JavaFX-21-blue?style=for-the-badge"/>
<img src="https://img.shields.io/badge/Maven-3.8+-red?style=for-the-badge&logo=apachemaven"/>
<img src="https://img.shields.io/badge/SQLite-Database-003B57?style=for-the-badge&logo=sqlite"/>
<img src="https://img.shields.io/badge/Version-2.0.0-success?style=for-the-badge"/>
<img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge"/>

<br><br>


</div>

---

## ✨ Overview

ABYSS Toolkit is a desktop application built with **JavaFX 21** designed for security professionals, penetration testers, bug bounty hunters and cybersecurity enthusiasts.

The platform combines:

* 🌐 Network Reconnaissance
* 🔒 Web Security Analysis
* 🕵️ OSINT Collection
* ⚔️ Security Testing
* 📊 Automated Reporting
* 💾 Persistent Data Storage

into a single modern interface.

---

# 🚀 Features

## 🌐 Network Reconnaissance

| Tool            | Description                                                  |
| --------------- | ------------------------------------------------------------ |
| Port Scanner    | Fast TCP scanning with service detection and banner grabbing |
| Host Discovery  | ICMP enumeration with latency measurements                   |
| DNS Toolkit     | Complete DNS record analysis                                 |
| Network Mapping | Host identification and fingerprinting                       |

### Highlights

✔ Top Ports Scan

✔ Custom Range Scan

✔ Full 1-65535 Scan

✔ Banner Grabbing

✔ Service Detection

✔ Latency Tracking

---

## 🔒 Web Security Analysis

### Security Headers Analyzer

Analyze:

* CSP
* HSTS
* X-Frame-Options
* X-Content-Type-Options
* Referrer Policy
* Permissions Policy

Includes:

* Security Score
* Risk Assessment
* Hardening Recommendations

### SSL/TLS Inspector

Features:

* Certificate Validation
* Cipher Suite Analysis
* Protocol Enumeration
* Expiration Monitoring
* Security Rating System

### Technology Fingerprinting

Detect:

* WordPress
* Joomla
* Drupal
* Magento
* Shopify
* React
* Angular
* Vue
* Apache
* Nginx
* IIS
* Cloudflare

---

## 🕵️ OSINT Toolkit

### Whois Lookup

Retrieve:

* Registrar
* Creation Date
* Expiration Date
* Nameservers
* Domain Status

### DNS Enumeration

Enumerate:

* A
* AAAA
* MX
* TXT
* NS
* SOA
* SRV
* CNAME

### GeoIP Intelligence

Identify:

* Country
* Region
* City
* ASN
* ISP
* Organization

---

## ⚔️ Security Testing

### Directory Bruteforce

Gobuster-style discovery supporting:

```text
php
html
txt
asp
aspx
jsp
```

### SQL Injection Scanner

Detection techniques:

* Error Based
* Boolean Based
* Time Based

### Additional Modules

* Subdomain Scanner
* CMS Detector
* Email Extractor

---

## 🛠 Utilities

### Cryptography

* MD5
* SHA1
* SHA256
* SHA512
* CRC32

### Encoding

* Base64
* URL Encoding
* Hex Encoding

### JWT Viewer

* Header Parsing
* Payload Parsing
* Signature Inspection

---

# 📊 Reporting Engine

Export findings to:

| Format   | Supported |
| -------- | --------- |
| PDF      | ✅         |
| HTML     | ✅         |
| JSON     | ✅         |
| XLSX     | ✅         |
| CSV      | ✅         |
| Markdown | ✅         |

Additional Features:

* Persistent Scan History
* Dashboard Statistics
* Activity Tracking
* Report Archiving

---

# 🖥 Dashboard

The integrated dashboard provides:

* Total Scans
* Reports Generated
* Hosts Analyzed
* Security Tests Executed
* Historical Activity
* CSV Export


---

# ⚡ Quick Start

## Clone Repository

```bash
git clone https://github.com/Underscore000/abyss-toolkit.git
cd abyss-toolkit
```

## Build

```bash
mvn clean package
```

## Run

```bash
mvn javafx:run
```

---

# 📦 Installation

## Requirements

| Component  | Version |
| ---------- | ------- |
| Java       | 21+     |
| Maven      | 3.8+    |
| JavaFX SDK | 21+     |

---

## Standalone Execution

### Windows

```bash
java --module-path "C:\JavaFX\lib" \
--add-modules javafx.controls,javafx.fxml \
-jar target/abyss-toolkit-2.0.0.jar
```

### Linux / macOS

```bash
java --module-path /opt/javafx/lib \
--add-modules javafx.controls,javafx.fxml \
-jar target/abyss-toolkit-2.0.0.jar
```

---

# 🏗 Technology Stack

| Layer         | Technology      |
| ------------- | --------------- |
| UI            | JavaFX 21       |
| Language      | Java 21         |
| Build         | Maven           |
| Database      | SQLite          |
| Networking    | Java NIO        |
| HTTP          | Java HttpClient |
| PDF Reports   | PDFBox          |
| Excel Reports | Apache POI      |
| JSON          | Jackson         |
| Parsing       | JSoup           |
| Logging       | SLF4J + Logback |
| Testing       | JUnit 5         |

---

# 📁 Project Structure

```text
abyss-toolkit
│
├── src
│   ├── main
│   │   ├── java
│   │   ├── resources
│   │   └── modules
│   │
│   └── test
│
├── docs
│   ├── screenshots
│   └── images
│
├── reports
│
├── pom.xml
│
└── README.md
```

---

# ⚙ Configuration

Default settings:

| Setting              | Value   |
| -------------------- | ------- |
| Theme                | Dark    |
| Threads              | 100     |
| Timeout              | 1000ms  |
| Auto Save            | Enabled |
| Dashboard on Startup | Enabled |

Database location:

```text
~/.abyss-toolkit/abyss.db
```

---

# 🤝 Contributing

Contributions are welcome.

```bash
fork ➜ create branch ➜ commit ➜ push ➜ pull request
```

Please:

* Follow Java 21 conventions
* Keep code modular
* Add meaningful logging
* Write tests where appropriate

---

# ⚠ Disclaimer

ABYSS Toolkit is intended solely for:

* Authorized Security Assessments
* Educational Purposes
* Research Activities

Users are responsible for complying with all applicable laws and regulations.

Unauthorized testing of systems without explicit permission may be illegal.

The developers assume no liability for misuse of this software.

---

# ⭐ Support The Project

If ABYSS Toolkit helps you, consider giving the repository a star.

It helps improve visibility and supports future development.

<div align="center">

### ⚡ ABYSS Toolkit

Where Security Meets Precision

</div>
