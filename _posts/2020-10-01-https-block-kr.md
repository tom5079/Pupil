---
layout: default
lang: ko
title: 접속 불가 현상 해결법
description: 히토미 뷰어 Pupil 접속 불가 현상 해결법
---

**현재 Intra/유니콘/Aspear 등으로 접속이 불가능합니다.**

**1줄요약: [PowerTunnel](https://github.com/krlvm/PowerTunnel-Android) / [1.1.1.1](https://play.google.com/store/apps/details?id=com.cloudflare.onedotonedotonedotone)쓰세요**

![로고]({{ "assets/images/warning.or.kr.jpg" | absolute_url  }})  
*익숙한 `그` 화면*

현재 일부 통신사에서 https 차단이 시행되고 있는 관계로 우회접속을 해야합니다.

# 해결 방법

## 1. PowerTunnel(속도저하 없음, 무료)
테스트해본 앱 중 유일하게 작동하는 앱입니다.
SNI 파편화로 작동하며, 외부 서버를 경유하지 않아 속도가 가장 빠릅니다.

[Github](https://github.com/krlvm/PowerTunnel-Android)

## 2. 1.1.1.1(속도저하 거의 없음, 무료)
Cloudflare에서 제공하는 검열 우회 앱입니다.
가까운 Cloudflare 서버를 경유하여 일반 VPN보다는 빠른 속도가 특징입니다.

[플레이스토어 링크](https://play.google.com/store/apps/details?id=com.cloudflare.onedotonedotonedotone)

## 3. VPN
확실한 해결 방법입니다.
추천 VPN으로는
* Tunnelbear(한 달 1.5GB 무료, 이후 유료)
* Betternet(무료)
* NordVPN(유료)  

가 있습니다. 속도는 NordVPN>Tunnelbear>Betternet순입니다.   
개인적으로 Tunnelbear을 추천드립니다.