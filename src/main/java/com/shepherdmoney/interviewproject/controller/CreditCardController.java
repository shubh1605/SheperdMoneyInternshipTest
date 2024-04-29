package com.shepherdmoney.interviewproject.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shepherdmoney.interviewproject.model.BalanceHistory;
import com.shepherdmoney.interviewproject.model.CreditCard;
import com.shepherdmoney.interviewproject.model.User;
import com.shepherdmoney.interviewproject.repository.BalanceHistoryRepository;
import com.shepherdmoney.interviewproject.repository.CreditCardRepository;
import com.shepherdmoney.interviewproject.repository.UserRepository;
import com.shepherdmoney.interviewproject.vo.request.AddCreditCardToUserPayload;
import com.shepherdmoney.interviewproject.vo.request.UpdateBalancePayload;
import com.shepherdmoney.interviewproject.vo.response.CreditCardView;

import net.minidev.json.JSONArray;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class CreditCardController {

    private final CreditCardRepository creditCardRepository;
    private final UserRepository userRepository;
    private final BalanceHistoryRepository balanceHistoryRepository; 

    @Autowired
    public CreditCardController(CreditCardRepository creditCardRepository, UserRepository userRepository, BalanceHistoryRepository balanceHistoryRepository) {
        this.creditCardRepository = creditCardRepository;
        this.userRepository = userRepository;
        this.balanceHistoryRepository = balanceHistoryRepository;
    }

    @PostMapping("/credit-card")
    public ResponseEntity<Integer> addCreditCardToUser(@RequestBody AddCreditCardToUserPayload payload) {
        try {
            Optional<User> optionalUser = userRepository.findById(payload.getUserId());
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                CreditCard creditCard = new CreditCard();
                creditCard.setIssuanceBank(payload.getCardIssuanceBank());
                creditCard.setNumber(payload.getCardNumber());
                creditCard.setOwner(user);
                creditCardRepository.save(creditCard);
                user.getCreditCards().add(creditCard);
                userRepository.save(user);
                return ResponseEntity.ok(creditCard.getId());
            } else {
                return ResponseEntity.badRequest().body(-1);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(-1);
        }
    }


    @GetMapping("/credit-card:all")
    public ResponseEntity<List<CreditCardView>> getAllCardOfUser(@RequestParam int userId) {
        try {
            Optional<User> optionalUser = userRepository.findById(userId);
            if (optionalUser.isPresent()) {
                List<CreditCard> creditCards = optionalUser.get().getCreditCards();
                List<CreditCardView> creditCardViews = creditCards.stream()
                        .map(creditCard -> new CreditCardView(creditCard.getIssuanceBank(), creditCard.getNumber()))
                        .collect(Collectors.toList());
                return ResponseEntity.ok(creditCardViews);
            } else {
                return ResponseEntity.ok().body(List.of());
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }


    @GetMapping("/credit-card:user-id")
    public ResponseEntity<Integer> getUserIdForCreditCard(@RequestParam String creditCardNumber) {
        try {
            Optional<CreditCard> optionalCreditCard = creditCardRepository.findByNumber(creditCardNumber);
            if (optionalCreditCard.isPresent()) {
                CreditCard creditCard = optionalCreditCard.get();
                User user = creditCard.getOwner();
                return ResponseEntity.ok(user.getId());
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(-1);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(-1);
        }
    }

    public static List<List<Object>> parseJsonToListOfLists(String jsonString) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(jsonString, new com.fasterxml.jackson.core.type.TypeReference<List<List<Object>>>() {});
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }


    @PostMapping("/credit-card:update-balance")
    public ResponseEntity<String> addNewBalanceEntry(@RequestBody UpdateBalancePayload[] payloads) {

        // Updating the balance of all the credit cards mentioned in the payload.
        // If a credit card is not found, it does not do anything. 
        // But if there is another credit card mentioned that is stored in the database, it will update the balances of that credit card.
        // I have stored a sorted list in the credit card list. But the first element is always the current date.

        Map<String, List<UpdateBalancePayload>> groupedPayload = new HashMap<>();
        for (UpdateBalancePayload updatePayload : payloads) {
            groupedPayload.computeIfAbsent(updatePayload.getCreditCardNumber(), k -> new ArrayList<>())
                    .add(updatePayload);
        }

        int isValidCall = 1;
        String error = "";
        for(Map.Entry<String, List<UpdateBalancePayload>> entry : groupedPayload.entrySet()){
            String key = entry.getKey();
            if(customAddBalanceEntry(entry.getValue()) == -1){
                isValidCall = -1;
                error +=  "\n"+key;
            }
        }
        if(isValidCall == -1){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Credit cards not found: "+error);
        } else {
            return ResponseEntity.ok("All credit cards updated");
        }
    }

    public int customAddBalanceEntry(List<UpdateBalancePayload> payloads){
        String creditCardNumber = payloads.get(0).getCreditCardNumber();
        Optional<CreditCard> optionalCreditCard = creditCardRepository.findByNumber(creditCardNumber);
        if (!optionalCreditCard.isPresent()) {
            return -1;
        }
        CreditCard creditCard = optionalCreditCard.get();
        List<BalanceHistory> balanceHistory = new ArrayList<>();
        if(creditCard.getBalanceHistoryString() != null){
            try {
                List<List<Object>> listOfLists = parseJsonToListOfLists(creditCard.getBalanceHistoryString());
                for(int obj = 1; obj<listOfLists.size(); obj++){
                    BalanceHistory bh = new BalanceHistory();
                    LocalDate dt = LocalDate.parse(listOfLists.get(obj).get(0).toString());
                    bh.setBalance((double) listOfLists.get(obj).get(1));
                    bh.setDate(dt);
                    balanceHistory.add(bh);
                }
                if(listOfLists.size() > 0){
                    BalanceHistory bh = new BalanceHistory();
                    LocalDate dt = LocalDate.parse(listOfLists.get(0).get(0).toString());
                    bh.setBalance((double) listOfLists.get(0).get(1));
                    bh.setDate(dt);
                    balanceHistory.add(bh);
                }
    
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try { 
            int balanceHistorySize = balanceHistory.size();
            int payloadSize = payloads.size();
            int i = 0;
            int j = 0;
            double diff = 0.0;
            double prevAmount = 0.0;
            List<List<Object>> myList = new ArrayList<>();

            
            List<BalanceHistory> newBalanceHistories = new ArrayList<>();
            Collections.sort(payloads, Comparator.comparing(UpdateBalancePayload::getBalanceDate));

            
            while(i<balanceHistorySize && j<payloadSize){
                LocalDate balanceDate = balanceHistory.get(i).getDate();
                LocalDate payLoaddate = payloads.get(j).getBalanceDate();
                if(balanceDate.compareTo(payLoaddate) == 0){
                    double balanceHistoryAmount =  balanceHistory.get(i).getBalance();
                    double payLoadBalance = payloads.get(j).getBalanceAmount();
                    diff = payLoadBalance - balanceHistoryAmount;
                    prevAmount = payLoadBalance;
                    i+=1;
                    j+=1;
                    List<Object> temp = new ArrayList<>();
                    temp.add(balanceDate.toString());
                    temp.add(payLoadBalance);
                    myList.add(temp);

                    if(diff != 0.00){
                        BalanceHistory newBalanceHistory = new BalanceHistory();
                        newBalanceHistory.setBalance(payLoadBalance);
                        newBalanceHistory.setDate(balanceDate);
                        newBalanceHistory.setCreditCard(creditCard);
                        newBalanceHistories.add(newBalanceHistory);
                        balanceHistoryRepository.save(newBalanceHistory);
                        creditCard.getBalanceHistory().add(newBalanceHistory);
                    }
                    
                } else if(balanceDate.compareTo(payLoaddate) < 0) {
                    LocalDate newDate = balanceDate;
                    while(newDate.compareTo(payLoaddate) < 0 && i<balanceHistorySize){
                        prevAmount = balanceHistory.get(i).getBalance() + diff;
                        List<Object> temp = new ArrayList<>();
                        temp.add(newDate.toString());
                        temp.add(prevAmount);
                        myList.add(temp);
                        if(diff != 0.00){
                            BalanceHistory newBalanceHistory = new BalanceHistory();
                            newBalanceHistory.setBalance(prevAmount);
                            newBalanceHistory.setDate(newDate);
                            newBalanceHistory.setCreditCard(creditCard);
                            newBalanceHistories.add(newBalanceHistory);

                            balanceHistoryRepository.save(newBalanceHistory);
                            creditCard.getBalanceHistory().add(newBalanceHistory);
                        }                   
                        newDate = newDate.plusDays(1);
                        i+=1;
                    }
                } else {
                    LocalDate newDate = payLoaddate;
                    while(newDate.compareTo(balanceDate) < 0){
                        prevAmount = payloads.get(j).getBalanceAmount();
                        List<Object> temp = new ArrayList<>();
                        temp.add(newDate.toString());
                        temp.add(prevAmount);
                        myList.add(temp);
                        BalanceHistory newBalanceHistory = new BalanceHistory();
                        newBalanceHistory.setBalance(prevAmount);
                        newBalanceHistory.setDate(newDate);
                        newBalanceHistory.setCreditCard(creditCard);
                        balanceHistoryRepository.save(newBalanceHistory);
                        creditCard.getBalanceHistory().add(newBalanceHistory);
                        newBalanceHistories.add(newBalanceHistory);
                        newDate = newDate.plusDays(1);
                    }
                    diff = payloads.get(j).getBalanceAmount() - balanceHistory.get(i).getBalance();
                    prevAmount = payloads.get(j).getBalanceAmount();
                    List<Object> temp = new ArrayList<>();
                    temp.add(newDate.toString());
                    temp.add(prevAmount);
                    myList.add(temp);
                    BalanceHistory newBalanceHistory = new BalanceHistory();
                    newBalanceHistory.setBalance(prevAmount);
                    newBalanceHistory.setDate(newDate);
                    newBalanceHistory.setCreditCard(creditCard);
                    balanceHistoryRepository.save(newBalanceHistory);
                    creditCard.getBalanceHistory().add(newBalanceHistory);
                    newBalanceHistories.add(newBalanceHistory);
                    newDate = newDate.plusDays(1);
                    j+=1;   
                    i+=1;
                }
            }
            while(i<balanceHistorySize){
                prevAmount = balanceHistory.get(i).getBalance() + diff;
                List<Object> temp = new ArrayList<>();
                temp.add(balanceHistory.get(i).getDate().toString());
                temp.add(prevAmount);
                myList.add(temp);
                BalanceHistory newBalanceHistory = new BalanceHistory();
                newBalanceHistory.setBalance(prevAmount);
                newBalanceHistory.setDate(balanceHistory.get(i).getDate());
                newBalanceHistory.setCreditCard(creditCard);
                balanceHistoryRepository.save(newBalanceHistory);
                creditCard.getBalanceHistory().add(newBalanceHistory);
                newBalanceHistories.add(newBalanceHistory);
                i+=1;
            }
            LocalDate startDate;
            if(i==0){
                startDate = payloads.get(j).getBalanceDate();
            } else {
                startDate = balanceHistory.get(balanceHistorySize - 1).getDate();
                startDate = startDate.plusDays(1);
            }
            LocalDate today = LocalDate.now();
            while(j<payloadSize || startDate.compareTo(today) <= 0){
                if(j<payloadSize && startDate.compareTo(payloads.get(j).getBalanceDate()) == 0){
                    List<Object> temp = new ArrayList<>();
                    temp.add(startDate.toString());
                    temp.add(payloads.get(j).getBalanceAmount());
                    myList.add(temp);
                    BalanceHistory newBalanceHistory = new BalanceHistory();
                    newBalanceHistory.setBalance(payloads.get(j).getBalanceAmount());
                    newBalanceHistory.setDate(startDate);
                    newBalanceHistory.setCreditCard(creditCard);
                    balanceHistoryRepository.save(newBalanceHistory);
                    creditCard.getBalanceHistory().add(newBalanceHistory);
                    newBalanceHistories.add(newBalanceHistory);
                    prevAmount = payloads.get(j).getBalanceAmount();
                    j += 1;
                    startDate = startDate.plusDays(1);
                } else {
                    if(j<payloadSize){
                        while(startDate.compareTo(payloads.get(j).getBalanceDate()) < 0){
                            List<Object> temp = new ArrayList<>();
                            temp.add(startDate.toString());
                            temp.add(prevAmount);
                            myList.add(temp);
                            
                            BalanceHistory newBalanceHistory = new BalanceHistory();
                            newBalanceHistory.setBalance(prevAmount);
                            newBalanceHistory.setDate(startDate);
                            newBalanceHistory.setCreditCard(creditCard);
                            balanceHistoryRepository.save(newBalanceHistory);  
                            creditCard.getBalanceHistory().add(newBalanceHistory); 
                            newBalanceHistories.add(newBalanceHistory);
             
                            startDate = startDate.plusDays(1);
                        }
                    } else {
                        while(startDate.compareTo(today) <= 0){
                            List<Object> temp = new ArrayList<>();
                            temp.add(startDate.toString());
                            temp.add(prevAmount);
                            myList.add(temp);
                            BalanceHistory newBalanceHistory = new BalanceHistory();
                            newBalanceHistory.setBalance(prevAmount);
                            newBalanceHistory.setDate(startDate);
                            newBalanceHistory.setCreditCard(creditCard);
                            balanceHistoryRepository.save(newBalanceHistory);  
                            creditCard.getBalanceHistory().add(newBalanceHistory);   
                            newBalanceHistories.add(newBalanceHistory);
                            startDate = startDate.plusDays(1);
                        }
                    }
                }
            }
            ObjectMapper objectMapper = new ObjectMapper();
            String json = "";
            myList.add(0, myList.remove(myList.size()-1));
            try {
                json = objectMapper.writeValueAsString(myList);
                creditCard.setBalanceHistoryString(json);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            creditCardRepository.save(creditCard);
            return 1;
        } catch (Exception e) {
            return -1;
        }
    }

    
}
