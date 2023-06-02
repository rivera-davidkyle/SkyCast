import boto3

def lambda_handler(event, context):
    dynamodb = boto3.resource('dynamodb')
    table_name = 'weather-tb'
    table = dynamodb.Table(table_name)
    response = table.scan()
    with table.batch_writer() as batch:
        for item in response['Items']:
            batch.delete_item({
                'latlon': item['latlon']
            })
    return({
        "request_code": 200,
        "desc": "Finished deleting"
    })
