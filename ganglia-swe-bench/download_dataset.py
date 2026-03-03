import json
import os
import urllib.request
import urllib.error

# Download a small subset of SWE-bench Lite (validation split usually smaller, but let's grab the raw parquet/json if possible)
# SWE-bench Lite is available on HF: https://huggingface.co/datasets/princeton-nlp/SWE-bench_Lite/resolve/main/data/test-00000-of-00001.parquet
# Since parquet requires extra dependencies, we will use the datasets library.

def main():
    try:
        from datasets import load_dataset
        print("Loading dataset via HuggingFace datasets library...")
        # Proxy is picked up by datasets automatically via HTTP_PROXY/HTTPS_PROXY env vars
        dataset = load_dataset("princeton-nlp/SWE-bench_Lite", split="test")
        
        # Take just first 5 for local testing
        subset = dataset.select(range(5))
        
        out_file = "ganglia-swe-bench/target/swe_bench_lite_subset.jsonl"
        os.makedirs("ganglia-swe-bench/target", exist_ok=True)
        
        with open(out_file, "w", encoding="utf-8") as f:
            for item in subset:
                f.write(json.dumps(item) + "\n")
                
        print(f"Successfully saved {len(subset)} records to {out_file}")
    except ImportError:
        print("Please install 'datasets' package: pip install datasets")

if __name__ == "__main__":
    main()
