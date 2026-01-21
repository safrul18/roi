package com.util;

public class DerivedWallet {

	private int index;
    private String address;
    private String privateKey;

    public DerivedWallet(int index,String address, String privateKey) {
    	this.index = index;
        this.address = address;
        this.privateKey = privateKey;
    }

    public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}

	public String getAddress() {
        return address;
    }

    public String getPrivateKey() {
        return privateKey;
    }
}