(function () {
  const FALLBACK_COLORS = ["#3b82f6", "#60a5fa", "#22c55e", "#ffc34d", "#ff8e79", "#64748b"];

  function decodeBase64Url(value) {
    const normalized = String(value || "").replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const binary = atob(padded);
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
    if (window.TextDecoder) {
      return new TextDecoder().decode(bytes);
    }
    let text = "";
    bytes.forEach((byte) => {
      text += String.fromCharCode(byte);
    });
    return decodeURIComponent(escape(text));
  }

  function readPayloadFromUrl(fallback) {
    const params = new URLSearchParams(window.location.search);
    const encoded = params.get("payload");
    if (!encoded) return fallback || {};
    try {
      return JSON.parse(decodeBase64Url(encoded));
    } catch (error) {
      return fallback || {};
    }
  }

  function escapeHtml(value) {
    return String(value || "").replace(/[&<>"']/g, (char) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;"
    })[char]);
  }

  function setText(id, value) {
    const element = document.getElementById(id);
    if (element) element.textContent = String(value || "");
  }

  function number(value) {
    const parsed = Number(value || 0);
    return Number.isFinite(parsed) ? Math.max(0, Math.floor(parsed)) : 0;
  }

  function compact(value, maxLength) {
    const text = String(value || "").replace(/\s+/g, " ").trim();
    return text.length > maxLength ? `${text.slice(0, maxLength - 1)}…` : text;
  }

  function formatNumber(value) {
    return number(value).toLocaleString("zh-CN");
  }

  function formatDateLabel(date) {
    const text = String(date || "");
    if (/^\d{4}-\d{2}-\d{2}$/.test(text)) return text.slice(5);
    if (/^\d{4}-\d{2}$/.test(text)) return text.slice(5);
    return text || "未知";
  }

  function formatGeneratedAt(value) {
    const date = new Date(value || Date.now());
    if (Number.isNaN(date.getTime())) return "刚刚生成";
    return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")} 生成`;
  }

  function hashIndex(value, length) {
    const text = String(value || "");
    let hash = 0;
    for (let index = 0; index < text.length; index += 1) {
      hash = (hash * 31 + text.charCodeAt(index)) >>> 0;
    }
    return length > 0 ? hash % length : 0;
  }

  function colorFor(value) {
    return FALLBACK_COLORS[hashIndex(value, FALLBACK_COLORS.length)];
  }

  function countBy(items, getKey) {
    const counts = new Map();
    items.forEach((item) => {
      const key = getKey(item);
      if (!key) return;
      counts.set(key, (counts.get(key) || 0) + 1);
    });
    return Array.from(counts.entries())
      .map(([label, count]) => ({ label, count }))
      .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label, "zh-CN"));
  }

  function normalizeRecords(records) {
    return Array.isArray(records) ? records.map((record) => ({
      id: String(record.id || ""),
      date: String(record.date || "未知日期"),
      title: compact(record.title || "未命名记录", 34),
      summary: compact(record.summary || "", 86),
      folder: String(record.folder || ""),
      tags: Array.isArray(record.tags) ? record.tags.map(String).filter(Boolean) : []
    })) : [];
  }

  window.MindFlowCard = {
    colorFor,
    compact,
    countBy,
    escapeHtml,
    formatDateLabel,
    formatGeneratedAt,
    formatNumber,
    normalizeRecords,
    number,
    readPayloadFromUrl,
    setText
  };
}());
