from openai import AzureOpenAI
from typing import List, Dict, Any

from events import Event


class AzureContentGenerator:
    def __init__(self,
                 openai_endpoint: str,
                 openai_key: str,
                 deployment_name: str = "gpt-35-turbo",  # or your deployment name
                 temperature: float = 0.7):
        """
        Initialize Azure services for content generation
        """

        # Initialize Azure OpenAI
        self.openai_client = AzureOpenAI(
            azure_endpoint=openai_endpoint,
            api_key=openai_key,
            api_version="2024-02-15-preview"
        )

        self.deployment_name = deployment_name
        self.temperature = temperature

    def _generate_base_content(self, event: Event) -> str:
        """Generate base content using Azure OpenAI"""
        system_prompt = """
        You are a social media user. Pick ONE random approach:

        Group 1:
        "Sure, like that's real"
        "Great, another..."
        "Why always during rush hour"
        
        Group 2:
        "Oh great, just what we needed"
        "Me trying to..."
        "Nobody: Me:"
        
        Group 3:
        "Can't believe this!!"
        "Well done"
        "Wish I was there"
        
        Group 4:
        "Interesting how..."
        "Makes sense given..."
        "Traffic stopped at..."
        
        Match the STYLE of your chosen approach and NEVER mention which one you chose.
        
        Make sure the event name is clearly mentioned and it is clear what this post is about.
        
        Keep messages short: between 50 and 100 characters. !important!
        
        Constraints:
        - Maximum 1 hashtag (!IMPORTANT!)
        - Write as if posting in the moment
        """

        user_prompt = f"""
        Generate a single social media post based on:

        Event: {event.event_name}
        Details: {event.details}
        Impact: {event.impact}
        Engagement: {event.engagement}
        Keywords: {event.keywords}
        """

        response = self.openai_client.chat.completions.create(
            model=self.deployment_name,
            temperature=self.temperature,
            response_format={"type": "text"},
            messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt}
                ]
        )

        return response.choices[0].message.content.strip()

    def generate_message(self, event) -> Dict[str, Any]:
        """Generate a complete message with analysis"""

        # Generate content
        text = self._generate_base_content(event)

        return {
            'event_name': event.event_name,
            'category': event.category,
            'text': text,
        }

    def generate_batch(self,
                       event: Event,
                       batch_size: int,
                       max_retries: int = 3) -> List[Dict[str, Any]]:
        """Generate a batch of messages with retry logic"""
        messages = []

        for i in range(batch_size):
            retries = 0
            while retries < max_retries:
                try:
                    message = self.generate_message(event)
                    messages.append(message)
                    break
                except Exception as e:
                    print(f"Error generating message (attempt {retries + 1}): {e}")
                    retries += 1
                    if retries == max_retries:
                        print(f"Failed to generate message after {max_retries} attempts")

        return messages
