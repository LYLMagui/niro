import requests
import json

def test_c5_response():
    url = "https://openapi.c5game.com/merchant/market/v2/products/condition/hash/name"
    
    # Query parameters
    params = {
        "app-key": "32a417bee57a445a9a09e58405686927"
    }
    
    # Request body
    data = {
        "appId": 730,
        "marketHashName": "Sealed Genesis Terminal",
        "pageNum": 1,
        "pageSize": 10
    }
    
    headers = {
        "Content-Type": "application/json"
    }
    
    try:
        print(f"Requesting {url}...")
        response = requests.post(url, params=params, json=data, headers=headers, timeout=10)
        
        print(f"Status Code: {response.status_code}")
        
        if response.status_code == 200:
            json_data = response.json()
            # Print pretty JSON
            print("Response JSON:")
            print(json.dumps(json_data, indent=2, ensure_ascii=False))
        else:
            print(f"Error: {response.text}")
            
    except Exception as e:
        print(f"Exception occurred: {str(e)}")

if __name__ == "__main__":
    test_c5_response()
