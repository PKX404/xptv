import express from "express";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT || 3000;

// Serve static files from public folder
app.use(express.static(path.join(__dirname, "public")));

// Serve index.html for root
app.get("/", (req, res) => {
  res.sendFile(path.join(__dirname, "index.html"));
});
app.get("/health", (req, res) => {
  res.send(">>> New server code deployed <<<");
});
app.listen(PORT, () => {
  console.log(">>> Starting new server code <<<");
  console.log(`Server running on port ${PORT}`);
});
