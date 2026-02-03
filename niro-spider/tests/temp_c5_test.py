import requests

def test_order_detail_v2():
    url = "https://openapi.c5game.com/merchant/order/v2/buy/detail"
    params = {
        "orderId": "1491722135393087488",
        "app-key": "32a417bee57a445a9a09e58405686927"
    }
    
    print(f"Testing URL: {url}")
    try:
        response = requests.get(url, params=params)
        print(f"Status Code: {response.status_code}")
        print(f"Response: {response.text}")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    test_order_detail_v2()
