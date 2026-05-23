
const http = require("http");
const https = require("https");

const PORT = 4000;
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;

http.createServer((req, res) => {
    if (!GEMINI_API_KEY) {
        res.writeHead(500, { "Content-Type": "application/json; charset=utf-8" });
        res.end(JSON.stringify({ error: "GEMINI_API_KEY 환경변수가 설정되지 않았습니다." }));
        return;
    }

    let body = "";
    req.on("data", chunk => body += chunk);
    req.on("end", () => {
        let promptText = "Hello";
        try {
            const parsed = JSON.parse(body);
            if (parsed.messages && parsed.messages.length > 0) {
                promptText = parsed.messages[parsed.messages.length - 1].content;
            } else if (parsed.prompt) {
                promptText = parsed.prompt;
            }
        } catch (e) {}

        const geminiPayload = JSON.stringify({
            contents: [{ parts: [{ text: promptText }] }]
        });

        const geminiReq = https.request({
            hostname: "generativelanguage.googleapis.com",
            path: `/v1beta/models/gemini-1.5-flash:generateContent?key=${GEMINI_API_KEY}`,
            method: "POST",
            headers: { "Content-Type": "application/json" }
        }, geminiRes => {
            let resBody = "";
            geminiRes.on("data", chunk => resBody += chunk);
            geminiRes.on("end", () => {
                try {
                    const geminiJson = JSON.parse(resBody);
                    const aiReply = geminiJson.candidates[0].content.parts[0].text;
                    
                    const openAiResponse = {
                        choices: [{ message: { role: "assistant", content: aiReply }, finish_reason: "stop" }]
                    };
                    res.writeHead(200, { "Content-Type": "application/json; charset=utf-8" });
                    res.end(JSON.stringify(openAiResponse));
                } catch (e) {
                    res.writeHead(500);
                    res.end(JSON.stringify({ error: "Gemini 응답 파싱 실패", raw: resBody }));
                }
            });
        });

        geminiReq.on("error", error => {
            res.writeHead(502, { "Content-Type": "application/json; charset=utf-8" });
            res.end(JSON.stringify({ error: "Gemini 요청 실패", message: error.message }));
        });

        geminiReq.write(geminiPayload);
        geminiReq.end();
    });
}).listen(PORT, () => {
    console.log("🚀 [Gemini 중계기] 내 컴퓨터 4000번 포트에서 정상 가동 중...");
});

