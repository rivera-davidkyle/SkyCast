import json
import boto3
import urllib.request
import logging
from decimal import Decimal

# Configure the logging module
logger = logging.getLogger()
logger.setLevel(logging.INFO)

def lambda_handler(event, context):
    lat = event['lat']
    lon = event['lon']
    # Connect to DynamoDB
    dynamodb = boto3.resource('dynamodb')
    table_name = 'weather-tb'
    table = dynamodb.Table(table_name)
    # Check if the data exists in DynamoDB
    key = get_bucket_key(lat,lon)
    response = table.get_item(
        Key={
            'latlon':key
        }
    )
    if 'Item' in response:
        # Data exists in cache, retrieve and return it
        cached_data = response['Item']
        item = response['Item']
        wind_spd = item['wind_spd']
        weather_desc = item['weather_desc']
        temp = item['temp']
        app_temp = item['app_temp']
        humidity = item['humidity']
        return {
            'statusCode': 200,
            'body': {
                'wind_spd': wind_spd,
                'weather_desc': weather_desc,
                'temp': temp,
                'app_temp': app_temp,
                'humidity': humidity
                }
            }
    else:
        url = f"https://api.weatherbit.io/v2.0/current?lat={lat}&lon={lon}&key={INSERT API-KEY}&units=I"
        try:
            response = urllib.request.urlopen(url)
            data = json.loads(response.read())
            data = data['data'][0]
            wind_spd =  Decimal(str(data['wind_spd']))
            weather_desc = data['weather']['description']
            temp = Decimal(str(data['temp']))
            app_temp = Decimal(str(data['app_temp']))
            humidity = Decimal(str(data['rh']))
            logger.info('Retrieved data: %s', data)
            # Create a new item to be stored in DynamoDB
            item = {
                'latlon': key,
                'wind_spd': wind_spd,
                'weather_desc': weather_desc,
                'temp': temp,
                'app_temp': app_temp,
                'humidity': humidity
            }
            # Put the item into DynamoDB
            table.put_item(Item=item)
        except Exception as e:
            return {
                'statusCode': 500,
                'body': json.dumps({'message': str(e)})
            }
    
        return {
            "statusCode": 200,
            "body": item
        }
        
def get_bucket_key(latitude, longitude):
    # Define the bucket size in degrees
    bucket_size = 5

    # Calculate the lower bound of the bucket range
    lower_bound_lat = int(latitude // bucket_size) * bucket_size

    # Calculate the upper bound of the bucket range
    upper_bound_lat = lower_bound_lat + bucket_size
    
    # Calculate the lower bound of the bucket range
    lower_bound_lon = int(longitude // bucket_size) * bucket_size

    # Calculate the upper bound of the bucket range
    upper_bound_lon = lower_bound_lon + bucket_size

    # Create the bucket key as a string in the format "lower_bound-upper_bound"
    bucket_key = f"{lower_bound_lat}-{upper_bound_lat}//{lower_bound_lon}-{upper_bound_lon}"

    return bucket_key