import { buildAuthHeaders } from "../utils/auth.js";

const BASE_URL = import.meta.env.VITE_API_BASE_URL || "";

async function parseResponse(response) {
  const text = await response.text();
  const data = text ? JSON.parse(text) : {};
  if (!response.ok || data.error) {
    throw new Error(data.error || data.message || "Request failed");
  }
  return data;
}

export const api = {
  getBaseUrl() {
    return BASE_URL;
  },

  async get(path) {
    const response = await fetch(`${BASE_URL}${path}`, {
      headers: buildAuthHeaders(path),
    });
    return parseResponse(response);
  },

  async json(path, body, method = "POST") {
    const response = await fetch(`${BASE_URL}${path}`, {
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
    const response = await fetch(`${BASE_URL}${path}`, {
      method: "POST",
      headers: buildAuthHeaders(path),
      body: formData,
    });
    return parseResponse(response);
  },

  async delete(path) {
    const response = await fetch(`${BASE_URL}${path}`, {
      method: "DELETE",
      headers: buildAuthHeaders(path),
    });
    return parseResponse(response);
  }
};
