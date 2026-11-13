
import express from "express";

const app = express();
app.use(express.json({ limit: "1mb" }));

const apiKey = process.env.GEMINI_API_KEY;

if (!apiKey) {
  console.error("Missing GEMINI_API_KEY environment variable.");
  process.exit(1);
}

const instructions = {
  "uz-UZ": `Sen JARVIS nomli aqlli, muloyim va aniq shaxsiy yordamchisan.
Foydalanuvchi bilan o'zbek tilida tabiiy suhbatlash. Ruscha yoki inglizcha gapirsa, o'sha tilda javob ber.
Telefonni boshqarish bo'yicha faqat ilova ruxsat bergan amallarni taklif qil.
Hech qachon bajarmagan amalni bajardim deb aytma.
Qo'ng'iroq, xabar, fayl o'chirish, xarid, to'lov va xavfsizlik sozlamalaridan oldin tasdiq talab qil.
Javoblaring qisqa, aniq va tabiiy bo'lsin.`,
  "ru-RU": `Ты JARVIS, умный и вежливый персональный помощник. Отвечай на языке пользователя. Не утверждай, что выполнил действие, если приложение этого не подтвердило. Перед звонками, сообщениями, удалением файлов, покупками, платежами и изменением настроек безопасности требуется подтверждение пользователя.`,
  "en-US": `You are JARVIS, a helpful and precise personal assistant. Reply in the user's language. Never claim an action was completed unless the app confirms it. Require confirmation before calls, messages, file deletion, purchases, payments or security changes.`
};

app.post("/chat", async (req, res) => {
  try {
    const { message, language = "uz-UZ", history = [] } = req.body ?? {};

    if (typeof message !== "string" || !message.trim()) {
      return res.status(400).json({ error: "message is required" });
    }

    const safeHistory = Array.isArray(history)
      ? history.slice(-16).filter(x =>
          x &&
          ["user", "assistant"].includes(x.role) &&
          typeof x.content === "string"
        )
      : [];

    const contents = safeHistory.map(x => ({
      role: x.role === "assistant" ? "model" : "user",
      parts: [{ text: x.content }]
    }));

    const last = safeHistory[safeHistory.length - 1];

    if (!(last && last.role === "user" &&
          last.content === message.trim())) {
      contents.push({
        role: "user",
        parts: [{ text: message.trim() }]
      });
    }

    const model = process.env.GEMINI_MODEL || "gemini-3.8-flash";

    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
      {
        method: "POST",
        headers: {
          "x-goog-api-key": apiKey,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          systemInstruction: {
            parts: [{
              text: instructions[language] || instructions["uz-UZ"]
            }]
          },
          contents,
          generationConfig: {
            temperature: 0.7,
            maxOutputTokens: 1024
          }
        })
      }
    );

    const data = await response.json();

    if (!response.ok) {
      console.error("Gemini API error:", data);
      return res.status(502).json({
        error: "AI provider request failed"
      });
    }

    const reply = (data.candidates?.[0]?.content?.parts || [])
      .filter(part => typeof part.text === "string")
      .map(part => part.text)
      .join("\n");

    return res.json({
      reply: reply || "Kechirasiz, hozir javob tayyorlay olmadim."
    });

  } catch (error) {
    console.error("Server error:", error);
    return res.status(500).json({
      error: "Internal server error"
    });
  }
});

app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});

app.listen(process.env.PORT || 3000, () => {
  console.log("JARVIS Gemini backend is running.");
});
