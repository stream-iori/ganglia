package me.stream.examples;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

public class KimiExample {

    public static void main(String[] args) {
        String apiKey = "sk-5PeYdPKvZwkM4tm1fks09vVhL39SAFVajnm2Nrir5l2xaju9";
        // Initialize the client with Kimi's base URL and the API key
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl("https://api.moonshot.cn/v1")
                .build();

        // Create a chat completion request
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.of("moonshot-v1-8k")) // Use Kimi's model name
                .addUserMessage("Hello, Kimi! Who are you?")
                .build();

        try {
            ChatCompletion completion = client.chat().completions().create(params);
            completion.choices().stream()
                    .map(choice -> choice.message().content().orElse(""))
                    .forEach(content -> System.out.println("Kimi: " + content));
        } catch (Exception e) {
            System.err.println("Error calling Kimi API: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Force exit to cleanup OkHttp threads if client doesn't support close()
            System.exit(0);
        }
    }
}
