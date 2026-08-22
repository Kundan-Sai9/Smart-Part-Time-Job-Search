import { buildAuthHeaders } from "../utils/auth.js";

async function parseResponse(response) {
  const text = await response.text();
  const data = text ? JSON.parse(text) : {};
  if (!response.ok || data.error) {
    throw new Error(data.error || data.message || "Request failed");
  }
  return data;
}

export const api = {
  async get(path) {
    const response = await fetch(path, {
      headers: buildAuthHeaders(path),
    });
    return parseResponse(response);
  },

  async json(path, body, method = "POST") {
    const response = await fetch(path, {
      method,
      headers: {
        "Content-Type": "application/json",
        ...buildAuthHeaders(path),
      },
      body: JSON.stringify(body),
    });
    return parseResponse(response);
  },

  async form(path, formData) {
    const response = await fetch(path, {
      method: "POST",
      headers: buildAuthHeaders(path),
      body: formData,
    });
    return parseResponse(response);
  },

  async delete(path) {
    const response = await fetch(path, {
      method: "DELETE",
      headers: buildAuthHeaders(path),
    });
    return parseResponse(response);
  }
};
