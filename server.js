import express from "express";

const app = express();
app.use(express.json({ limit: "1mb" }));

const apiKey = process.env.OPENAI_API_KEY;
if (!apiKey) {
  console.error("Missing OPENAI_API_KEY environment variable.");
  process.exit(1);
}

const instructions = {
  "uz-UZ": `Sen JARVIS nomli aqlli, muloyim va aniq shaxsiy yordamchisan.
Foydalanuvchi bilan o‘zbek tilida tabiiy suhbatlash. U ruscha yoki inglizcha gapirsa, o‘sha tilda javob ber.
Telefonni boshqarish bo‘yicha faqat ilova aniq ruxsat bergan amallarni taklif qil. Hech qachon o‘zingni amalni bajargandek ko‘rsatma. Har qanday qo‘ng‘iroq, xabar yuborish, faylni o‘chirish, xarid, to‘lov yoki xavfsizlik sozlamasini o‘zgartirishdan oldin foydalanuvchi tasdig‘i zarur.
Javobni oddiy matn ko‘rinishida, qisqa va tabiiy ber.`,
  "ru-RU": `Ты JARVIS, умный, вежливый и точный персональный помощник. Отвечай естественно на русском, узбекском или английском в соответствии с языком пользователя. Не утверждай, что выполнил действие, если приложение не подтвердило его. Перед звонками, отправкой сообщений, удалением файлов, покупками, платежами и изменением настроек безопасности требуется подтверждение пользователя.`,
  "en-US": `You are JARVIS, a helpful, precise personal assistant. Reply naturally in the user's language: Uzbek, Russian, or English. Never claim an action was completed unless the app confirms it. Require explicit confirmation before calls, sending messages, deleting files, purchases, payments, or changing security settings.`
};

app.post("/chat", async (req, res) => {
  try {
    const { message, language = "uz-UZ", history = [] } = req.body ?? {};
    if (typeof message !== "string" || !message.trim()) {
      return res.status(400).json({ error: "message is required" });
    }
    const safeHistory = Array.isArray(history)
      ? history.slice(-16).filter(x =>
          x && ["user", "assistant"].includes(x.role) &&
          typeof x.content === "string"
        )
      : [];
    const input = safeHistory.map(x => ({ role: x.role, content: x.content }));
    const last = input[input.length - 1];
    if (!(last && last.role === "user" && last.content === message.trim())) {
      input.push({ role: "user", content: message.trim() });
    }

    const response = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: process.env.OPENAI_MODEL || "gpt-4.1-mini",
        instructions: instructions[language] || instructions["uz-UZ"],
        input
      })
    });
    const data = await response.json();
    if (!response.ok) {
      console.error("OpenAI API error:", data);
      return res.status(502).json({ error: "AI provider request failed" });
    }
    const reply = (data.output || [])
      .flatMap(item => item.content || [])
      .filter(item => item.type === "output_text")
      .map(item => item.text)
      .join("\n");
    return res.json({ reply: reply || "Kechirasiz, hozir javob tayyorlay olmadim." });
  } catch (error) {
    console.error(error);
    return res.status(500).json({ error: "Internal server error" });
  }
});

app.get("/health", (_req, res) => res.json({ status: "ok" }));
app.listen(process.env.PORT || 3000, () => {
  console.log(`JARVIS backend listening on port ${process.env.PORT || 3000}`);
});
