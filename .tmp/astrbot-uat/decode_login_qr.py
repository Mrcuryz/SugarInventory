import cv2


image = cv2.imread("wechat-login-qr-current.png")
value, _, _ = cv2.QRCodeDetector().detectAndDecode(image)
print(value)
