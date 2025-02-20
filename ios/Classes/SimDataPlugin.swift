import Flutter
import UIKit
import MessageUI

public class SimDataPlugin: NSObject, FlutterPlugin, MFMessageComposeViewControllerDelegate {
    private var result: FlutterResult? // Store the FlutterResult callback
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "sim_data", binaryMessenger: registrar.messenger())
        let instance = SimDataPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "getPlatformVersion":
            result("iOS " + UIDevice.current.systemVersion)
            break
        case "send_sms":
            self.result = result // Store the FlutterResult callback
            Messages(call: call);
            break
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    func Messages(call: FlutterMethodCall) {
        if MFMessageComposeViewController.canSendText() == true {
            guard let args = call.arguments as? [String : Any] else {
                result?(false)
                return
            }
            let phoneNumber = args["phone"] as! String
            let msg = args["msg"] as! String
            let recipients:[String] = [phoneNumber]
            let messageController = MFMessageComposeViewController()
            messageController.messageComposeDelegate  = self
            messageController.recipients = recipients
            messageController.body = msg
            if let topViewController = UIApplication.shared.keyWindow?.rootViewController {
                topViewController.present(messageController, animated: true, completion: nil)
            } else {
                result?(false)
            }
        } else {
            result?(false)
        }
    }
    
    public func messageComposeViewController(_ controller: MFMessageComposeViewController, didFinishWith result: MessageComposeResult) {
        controller.dismiss(animated: true){
            switch result{
            case .sent:
                self.result?(true) // Return true if the message was sent
            case .cancelled, .failed:
                self.result?(false) // Return false if the message was cancelled or failed
            @unknown default:
                self.result?(false) // Handle any future cases
                
            }
        }
    }
}

