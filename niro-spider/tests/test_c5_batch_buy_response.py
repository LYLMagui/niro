import requests
import json
import uuid

def test_c5_batch_buy_response():
    url = "https://openapi.c5game.com/merchant/trade/v1/batch/buy"
    
    # Query parameters
    params = {
        "app-key": "32a417bee57a445a9a09e58405686927"
    }
    
    # Request body - using a dummy product that likely fails or just to see the structure
    # We need at least one item in the list to trigger the batch logic
    data = {
        "tradeUrl": "https://steamcommunity.com/tradeoffer/new/?partner=838116584&token=ONOlXNTF",
        "productList": [
            {
                "productId": 1491204572340678656, # Example ID from previous step
                "buyPrice": 1.6,
                "outTradeNo": str(uuid.uuid4())
            }
        ]
    }
    
    headers = {
        "Content-Type": "application/json"
    }
    
    try:
        print(f"Requesting {url}...")
        response = requests.post(url, params=params, json=data, headers=headers, timeout=10)
        
        print(f"Status Code: {response.status_code}")
        
        # Even if it fails (e.g. balance not enough), we expect a JSON response with structure
        if response.status_code == 200 or response.status_code == 400: 
            json_data = response.json()
            # Print pretty JSON
            print("Response JSON:")
            print(json.dumps(json_data, indent=2, ensure_ascii=False))
        else:
            print(f"Error: {response.text}")
            
    except Exception as e:
        print(f"Exception occurred: {str(e)}")

if __name__ == "__main__":
    test_c5_batch_buy_response()
