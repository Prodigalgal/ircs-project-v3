import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from uuid import uuid4

import torch
from fastapi.testclient import TestClient
from tokenizers import Tokenizer
from tokenizers.models import WordPiece
from tokenizers.pre_tokenizers import Whitespace
from transformers import BertConfig, BertForSequenceClassification, PreTrainedTokenizerFast

from app.classifier import AdultTextClassifier
from app.main import app
from app.schemas import ModelTextItem
from app.settings import ClassifierSettings


class ClassifierCompatibilityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.model_directory = tempfile.TemporaryDirectory(prefix="ircs-classifier-test-")
        path = Path(cls.model_directory.name)
        vocabulary = {word: index for index, word in enumerate(
            ["[PAD]", "[UNK]", "[CLS]", "[SEP]", "[MASK]", "safe", "example"])}
        tokenizer = Tokenizer(WordPiece(vocabulary, unk_token="[UNK]"))
        tokenizer.pre_tokenizer = Whitespace()
        PreTrainedTokenizerFast(tokenizer_object=tokenizer, unk_token="[UNK]", pad_token="[PAD]") \
            .save_pretrained(path)
        torch.manual_seed(1)
        model = BertForSequenceClassification(BertConfig(
            vocab_size=len(vocabulary), hidden_size=16, num_hidden_layers=1,
            num_attention_heads=2, intermediate_size=32,
            id2label={0: "safe", 1: "sexual"}, label2id={"safe": 0, "sexual": 1}))
        model.save_pretrained(path, safe_serialization=True)
        cls.classifier = AdultTextClassifier(ClassifierSettings(
            model_name=str(path), local_files_only=True, batch_size=1, torch_threads=1))

    @classmethod
    def tearDownClass(cls):
        cls.classifier = None
        cls.model_directory.cleanup()

    def test_latest_stack_runs_real_local_cpu_inference_and_preserves_ids(self):
        items = [ModelTextItem(id=uuid4(), text="safe example"), ModelTextItem(id=uuid4(), text="中文标题")]
        results = self.classifier.classify(items)
        self.assertEqual([item.id for item in results], [item.id for item in items])
        self.assertTrue(all(0 <= result.adultScore <= 1 for result in results))
        self.assertTrue(all(result.label in {"SAFE", "SUSPECT", "ADULT"} for result in results))
        self.assertEqual(self.classifier.classify([]), [])

    def test_http_health_and_input_validation_with_real_classifier(self):
        with patch("app.main.AdultTextClassifier", return_value=self.classifier), TestClient(app) as client:
            self.assertTrue(client.get("/health").json()["ready"])
            identifier = str(uuid4())
            response = client.post("/internal/v1/adult-classifier:classify", json={
                "items": [{"id": identifier, "text": "safe"}, {"id": "invalid", "text": "ignored"}]})
            self.assertEqual(response.status_code, 200)
            self.assertEqual([item["id"] for item in response.json()["items"]], [identifier])

    def test_internal_token_and_scope_remain_required(self):
        secured = ClassifierSettings(require_token=True, service_token="local-smoke-token")
        with patch("app.main.AdultTextClassifier", return_value=self.classifier), \
                patch("app.security.settings", secured), TestClient(app) as client:
            endpoint = "/internal/v1/adult-classifier:classify"
            self.assertEqual(client.post(endpoint, json={"items": []}).status_code, 401)
            headers = {"X-IRCS-SERVICE-ID": "smoke", "X-IRCS-SERVICE-TOKEN": "local-smoke-token",
                       "X-IRCS-SERVICE-SCOPES": "adult-classifier:classify"}
            self.assertEqual(client.post(endpoint, json={"items": []}, headers=headers).status_code, 200)


if __name__ == "__main__":
    unittest.main()
