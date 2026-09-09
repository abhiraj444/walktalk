@echo off
title WalkTalk Local Server
cd /d "%~dp0server"
echo ====================================================
echo Starting WalkTalk Local Server and Cloudflare Tunnel...
echo ====================================================
node src/index.js
pause
